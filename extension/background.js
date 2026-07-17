/**
 * Job Application Tracker — background service worker (MV3).
 *
 * Responsibilities:
 *  - Keep a cached list of known portals (synced from the portal-registry every 6h).
 *  - Classify the active tab's URL as known-portal / new-portal / ignore.
 *  - Broker authenticated calls to the backend (save job, add portal), reading the
 *    JWT from chrome.storage.local. It NEVER reads cookies or credentials.
 *  - Accept the JWT pushed in from the web app via externally_connectable.
 *
 * All backend traffic goes through the api-gateway at API_BASE.
 */

'use strict';

const API_BASE = 'http://localhost:8080/api/v1';
const PORTAL_SYNC_ALARM = 'portal-sync';
const PORTAL_SYNC_PERIOD_MINUTES = 6 * 60; // every 6 hours

// ---------------------------------------------------------------------------
// Storage helpers (Promise wrappers around chrome.storage.local)
// ---------------------------------------------------------------------------

function storageGet(keys) {
  return new Promise((resolve) => chrome.storage.local.get(keys, resolve));
}

function storageSet(obj) {
  return new Promise((resolve) => chrome.storage.local.set(obj, resolve));
}

async function getJwt() {
  const { jwt } = await storageGet('jwt');
  return jwt || null;
}

// ---------------------------------------------------------------------------
// Portal sync — GET /portals, cache domains + displayName map
// ---------------------------------------------------------------------------

/**
 * Fetch the known-portal catalog and cache it under `knownPortals`:
 *   { domains: string[], displayNames: { [domain]: string }, syncedAt: ISO }
 */
async function syncPortals() {
  try {
    const res = await fetch(`${API_BASE}/portals`, {
      method: 'GET',
      headers: { Accept: 'application/json' }
    });
    if (!res.ok) {
      console.warn('[JAT] portal sync failed with status', res.status);
      return;
    }
    const list = await res.json(); // [{ portalId, domain, displayName }]
    const domains = [];
    const displayNames = {};
    for (const p of Array.isArray(list) ? list : []) {
      if (!p || !p.domain) continue;
      const domain = String(p.domain).toLowerCase();
      domains.push(domain);
      displayNames[domain] = p.displayName || domain;
    }
    await storageSet({
      knownPortals: { domains, displayNames, syncedAt: new Date().toISOString() }
    });
    console.log(`[JAT] synced ${domains.length} portals`);
  } catch (err) {
    console.warn('[JAT] portal sync error', err);
  }
}

async function getKnownPortals() {
  const { knownPortals } = await storageGet('knownPortals');
  return knownPortals || { domains: [], displayNames: {} };
}

// ---------------------------------------------------------------------------
// URL classification
// ---------------------------------------------------------------------------

/** Strip a leading "www." from a hostname. */
function stripWww(host) {
  return host.replace(/^www\./i, '');
}

/** True if the URL path/query looks like an individual job posting. */
function looksLikeJobPage(url) {
  let u;
  try {
    u = new URL(url);
  } catch {
    return false;
  }
  const path = u.pathname.toLowerCase();
  const pathHit =
    path.includes('/jobs/') ||
    path.includes('/job/') ||
    path.includes('/careers/') ||
    path.includes('/viewjob');
  if (pathHit) return true;

  // Known job-id query params used by major portals.
  const jobParams = ['currentjobid', 'jobid', 'gh_jid', 'jk', 'postingid'];
  for (const key of u.searchParams.keys()) {
    if (jobParams.includes(key.toLowerCase())) return true;
  }
  return false;
}

/**
 * Classify a URL against the known-portal list.
 * @returns {'known-portal' | 'new-portal' | 'ignore'}
 *
 * knownPortals.domains are normalized (no scheme/path, no leading www). A host
 * matches a portal domain by suffix so subdomains (e.g. a company's Greenhouse
 * board on boards.greenhouse.io) still resolve to the parent portal.
 */
function classifyUrl(url, knownPortals) {
  let host;
  try {
    host = stripWww(new URL(url).hostname.toLowerCase());
  } catch {
    return 'ignore';
  }
  // Skip browser-internal and empty hosts.
  if (!host || !host.includes('.')) return 'ignore';

  const domains = (knownPortals && knownPortals.domains) || [];
  const isKnown = domains.some(
    (d) => host === d || host.endsWith('.' + d) || host.endsWith(d)
  );

  const jobPage = looksLikeJobPage(url);
  if (!jobPage) return 'ignore';
  return isKnown ? 'known-portal' : 'new-portal';
}

/** Resolve the registered displayName for a URL's host, if known. */
function displayNameForUrl(url, knownPortals) {
  let host;
  try {
    host = stripWww(new URL(url).hostname.toLowerCase());
  } catch {
    return null;
  }
  const map = (knownPortals && knownPortals.displayNames) || {};
  const domains = Object.keys(map);
  const match = domains.find(
    (d) => host === d || host.endsWith('.' + d) || host.endsWith(d)
  );
  return match ? map[match] : null;
}

/** Resolve the registered portalId's domain for a URL (the matched portal domain). */
function portalDomainForUrl(url, knownPortals) {
  let host;
  try {
    host = stripWww(new URL(url).hostname.toLowerCase());
  } catch {
    return null;
  }
  const domains = (knownPortals && knownPortals.domains) || [];
  return (
    domains.find((d) => host === d || host.endsWith('.' + d) || host.endsWith(d)) ||
    null
  );
}

// ---------------------------------------------------------------------------
// Badge — surface "NEW" when a new portal is detected on the active tab
// ---------------------------------------------------------------------------

async function updateBadgeForTab(tabId, url) {
  try {
    const known = await getKnownPortals();
    const c = classifyUrl(url || '', known);
    if (c === 'new-portal') {
      chrome.action.setBadgeText({ tabId, text: 'NEW' });
      chrome.action.setBadgeBackgroundColor({ tabId, color: '#d97706' });
    } else if (c === 'known-portal') {
      chrome.action.setBadgeText({ tabId, text: '•' });
      chrome.action.setBadgeBackgroundColor({ tabId, color: '#2563eb' });
    } else {
      chrome.action.setBadgeText({ tabId, text: '' });
    }
  } catch (err) {
    // Badge is best-effort only.
  }
}

chrome.tabs.onUpdated.addListener((tabId, changeInfo, tab) => {
  if (changeInfo.status === 'complete' && tab && tab.url) {
    updateBadgeForTab(tabId, tab.url);
  }
});

chrome.tabs.onActivated.addListener(async ({ tabId }) => {
  try {
    const tab = await chrome.tabs.get(tabId);
    if (tab && tab.url) updateBadgeForTab(tabId, tab.url);
  } catch {
    /* tab may be gone */
  }
});

// ---------------------------------------------------------------------------
// Backend calls
// ---------------------------------------------------------------------------

/** POST /applications with the JWT. Returns { status, body }. */
async function saveJob(payload) {
  const jwt = await getJwt();
  if (!jwt) {
    return { status: 401, authRequired: true, body: null };
  }
  try {
    const res = await fetch(`${API_BASE}/applications`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'application/json',
        Authorization: `Bearer ${jwt}`
      },
      body: JSON.stringify(payload)
    });
    const body = await res.json().catch(() => null);
    return { status: res.status, body };
  } catch (err) {
    return { status: 0, error: String(err), body: null };
  }
}

/** POST /portals { domain, displayName }. Returns { status, body }. */
async function addPortal({ domain, displayName }) {
  const jwt = await getJwt();
  try {
    const headers = {
      'Content-Type': 'application/json',
      Accept: 'application/json'
    };
    if (jwt) headers.Authorization = `Bearer ${jwt}`;
    const res = await fetch(`${API_BASE}/portals`, {
      method: 'POST',
      headers,
      body: JSON.stringify({ domain, displayName })
    });
    const body = await res.json().catch(() => null);
    // Refresh the local cache so the just-added portal is treated as known.
    if (res.ok) await syncPortals();
    return { status: res.status, body };
  } catch (err) {
    return { status: 0, error: String(err), body: null };
  }
}

// ---------------------------------------------------------------------------
// Message handling (from popup + content script)
// ---------------------------------------------------------------------------

chrome.runtime.onMessage.addListener((msg, sender, sendResponse) => {
  (async () => {
    try {
      switch (msg && msg.type) {
        case 'GET_CLASSIFICATION': {
          const known = await getKnownPortals();
          const url = msg.url || '';
          sendResponse({
            classification: classifyUrl(url, known),
            knownDisplayName: displayNameForUrl(url, known),
            portalDomain: portalDomainForUrl(url, known)
          });
          break;
        }
        case 'SAVE_JOB': {
          sendResponse(await saveJob(msg.payload || {}));
          break;
        }
        case 'ADD_PORTAL': {
          sendResponse(await addPortal(msg.payload || {}));
          break;
        }
        case 'SET_JWT': {
          if (msg.jwt) {
            await storageSet({ jwt: msg.jwt });
            sendResponse({ ok: true });
          } else {
            sendResponse({ ok: false, error: 'no jwt in message' });
          }
          break;
        }
        case 'GET_JWT_STATUS': {
          const jwt = await getJwt();
          sendResponse({ authenticated: !!jwt });
          break;
        }
        case 'SYNC_PORTALS': {
          await syncPortals();
          sendResponse({ ok: true });
          break;
        }
        default:
          sendResponse({ error: 'unknown message type' });
      }
    } catch (err) {
      sendResponse({ error: String(err) });
    }
  })();
  return true; // keep the message channel open for the async response
});

// ---------------------------------------------------------------------------
// External messages (from the web app — the JWT auth bridge)
// ---------------------------------------------------------------------------

chrome.runtime.onMessageExternal.addListener((msg, sender, sendResponse) => {
  (async () => {
    // Only accept the JWT hand-off from the web app. No other external commands.
    if (msg && msg.type === 'SET_JWT' && msg.jwt) {
      await storageSet({ jwt: msg.jwt });
      sendResponse({ ok: true });
      return;
    }
    // Revoke on logout / hard 401 in the web app.
    if (msg && msg.type === 'CLEAR_JWT') {
      await storageSet({ jwt: null });
      sendResponse({ ok: true });
      return;
    }
    if (msg && msg.type === 'GET_JWT_STATUS') {
      const jwt = await getJwt();
      sendResponse({ authenticated: !!jwt });
      return;
    }
    sendResponse({ ok: false, error: 'unsupported external message' });
  })();
  return true;
});

// ---------------------------------------------------------------------------
// Lifecycle — sync on install/startup and on a 6h alarm
// ---------------------------------------------------------------------------

chrome.runtime.onInstalled.addListener(() => {
  syncPortals();
  chrome.alarms.create(PORTAL_SYNC_ALARM, {
    periodInMinutes: PORTAL_SYNC_PERIOD_MINUTES
  });
});

chrome.runtime.onStartup.addListener(() => {
  syncPortals();
});

chrome.alarms.onAlarm.addListener((alarm) => {
  if (alarm.name === PORTAL_SYNC_ALARM) syncPortals();
});
