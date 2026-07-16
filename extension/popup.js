/**
 * Job Application Tracker — popup logic.
 *
 * On open:
 *   1. Find the active tab.
 *   2. Ask background to classify the tab URL (known-portal / new-portal / ignore).
 *   3. Ask the content script for visible page metadata to pre-fill the form.
 *   4. Render the banner (new portal), the pre-filled form, and auth state.
 *
 * Sending happens through the background service worker, which holds the JWT.
 */

'use strict';

// --- element handles ---------------------------------------------------------
const els = {
  authPrompt: document.getElementById('auth-prompt'),
  banner: document.getElementById('new-portal-banner'),
  bannerDomain: document.getElementById('new-portal-domain'),
  addPortalBtn: document.getElementById('add-portal-btn'),
  ignoreNotice: document.getElementById('ignore-notice'),
  form: document.getElementById('save-form'),
  jobTitle: document.getElementById('jobTitle'),
  company: document.getElementById('company'),
  notes: document.getElementById('notes'),
  externalIdWrap: document.getElementById('external-id-wrap'),
  externalJobId: document.getElementById('externalJobId'),
  saveBtn: document.getElementById('save-btn'),
  result: document.getElementById('result')
};

// State gathered during init, reused on submit.
const state = {
  tab: null,
  classification: 'ignore',
  portalDomain: null,     // matched known-portal domain (for portalId source)
  knownDisplayName: null, // registered displayName if known
  pageInfo: null,
  hostname: null
};

// --- messaging helpers -------------------------------------------------------

function sendToBackground(message) {
  return new Promise((resolve) => {
    chrome.runtime.sendMessage(message, (resp) => {
      if (chrome.runtime.lastError) {
        resolve({ error: chrome.runtime.lastError.message });
      } else {
        resolve(resp);
      }
    });
  });
}

function sendToTab(tabId, message) {
  return new Promise((resolve) => {
    chrome.tabs.sendMessage(tabId, message, (resp) => {
      if (chrome.runtime.lastError) {
        resolve(null); // content script may not be present (e.g. chrome:// pages)
      } else {
        resolve(resp);
      }
    });
  });
}

function getActiveTab() {
  return new Promise((resolve) => {
    chrome.tabs.query({ active: true, currentWindow: true }, (tabs) => {
      resolve(tabs && tabs[0] ? tabs[0] : null);
    });
  });
}

// --- UI helpers --------------------------------------------------------------

function show(el) { el.classList.remove('hidden'); }
function hide(el) { el.classList.add('hidden'); }

function showResult(kind, text) {
  els.result.className = `msg ${kind}`;
  els.result.textContent = text;
  show(els.result);
}

function hostnameOf(url) {
  try {
    return new URL(url).hostname.replace(/^www\./i, '').toLowerCase();
  } catch {
    return '';
  }
}

// Derive a friendly portal display name for prefills.
function suggestedDisplayName() {
  if (state.knownDisplayName) return state.knownDisplayName;
  if (state.pageInfo && state.pageInfo.company) return state.pageInfo.company;
  // Fall back to a capitalized hostname label, e.g. "jobs.acme.com" -> "Acme".
  const parts = (state.hostname || '').split('.');
  const core = parts.length >= 2 ? parts[parts.length - 2] : (state.hostname || '');
  return core ? core.charAt(0).toUpperCase() + core.slice(1) : (state.hostname || 'Unknown');
}

// --- initialization ----------------------------------------------------------

async function init() {
  const tab = await getActiveTab();
  state.tab = tab;

  if (!tab || !tab.url || !/^https?:/i.test(tab.url)) {
    els.ignoreNotice.textContent = 'This page can\'t be captured.';
    show(els.ignoreNotice);
    hide(els.form);
    return;
  }
  state.hostname = hostnameOf(tab.url);

  // Auth status — surface the login prompt if there's no JWT.
  const auth = await sendToBackground({ type: 'GET_JWT_STATUS' });
  if (!auth || !auth.authenticated) {
    show(els.authPrompt);
  }

  // Classification (known / new / ignore).
  const cls = await sendToBackground({ type: 'GET_CLASSIFICATION', url: tab.url });
  if (cls && !cls.error) {
    state.classification = cls.classification;
    state.portalDomain = cls.portalDomain;
    state.knownDisplayName = cls.knownDisplayName;
  }

  // Page metadata from the content script.
  state.pageInfo = (await sendToTab(tab.id, { type: 'GET_PAGE_INFO' })) || {};

  // Prefill the form.
  els.jobTitle.value = state.pageInfo.jobTitle || '';
  els.company.value = state.pageInfo.company || state.knownDisplayName || '';
  if (state.pageInfo.externalJobId) {
    els.externalJobId.value = state.pageInfo.externalJobId;
    show(els.externalIdWrap);
  }

  // Banners.
  if (state.classification === 'new-portal') {
    els.bannerDomain.textContent = state.hostname;
    show(els.banner);
  } else if (state.classification === 'ignore') {
    show(els.ignoreNotice);
  }
}

// --- actions -----------------------------------------------------------------

els.addPortalBtn.addEventListener('click', async () => {
  els.addPortalBtn.disabled = true;
  const payload = { domain: state.hostname, displayName: suggestedDisplayName() };
  const resp = await sendToBackground({ type: 'ADD_PORTAL', payload });

  if (resp && (resp.status === 200 || resp.status === 201)) {
    hide(els.banner);
    // Now treated as known; use its display name for the company prefill.
    if (resp.body && resp.body.displayName && !els.company.value) {
      els.company.value = resp.body.displayName;
    }
    state.classification = 'known-portal';
    state.portalDomain = state.hostname;
    state.knownDisplayName = payload.displayName;
    showResult('ok', `Added "${payload.displayName}" to known portals.`);
  } else if (resp && resp.status === 409) {
    hide(els.banner);
    showResult('info', 'That portal is already registered.');
  } else if (resp && resp.status === 401) {
    show(els.authPrompt);
    els.addPortalBtn.disabled = false;
    showResult('err', 'Log in to the web app first, then try again.');
  } else {
    els.addPortalBtn.disabled = false;
    showResult('err', 'Could not add the portal. Is the backend running?');
  }
});

els.form.addEventListener('submit', async (e) => {
  e.preventDefault();
  hide(els.result);
  els.saveBtn.disabled = true;
  els.saveBtn.textContent = 'Saving…';

  // portalName: prefer registered display name, else the company field, else hostname.
  const portalName = state.knownDisplayName || suggestedDisplayName();
  // portalId: the registry keys portals by domain; the backend resolves/creates by
  // domain, so we pass the matched portal domain (or current host) as the id source.
  const portalId = state.portalDomain || state.hostname;

  const payload = {
    externalJobId: state.pageInfo && state.pageInfo.externalJobId ? state.pageInfo.externalJobId : undefined,
    portalId,
    portalName,
    jobUrl: (state.tab && state.tab.url) || '',
    jobTitle: els.jobTitle.value.trim(),
    company: els.company.value.trim(),
    notes: els.notes.value.trim() || undefined,
    descriptionSnapshot: (state.pageInfo && state.pageInfo.description) || undefined
  };

  // Basic client-side validation (server enforces @NotBlank too).
  if (!payload.jobTitle || !payload.company) {
    showResult('err', 'Title and company are required.');
    els.saveBtn.disabled = false;
    els.saveBtn.textContent = 'Save to tracker';
    return;
  }

  const resp = await sendToBackground({ type: 'SAVE_JOB', payload });

  if (resp && resp.authRequired) {
    show(els.authPrompt);
    showResult('err', 'Log in to the web app first, then try again.');
  } else if (resp && (resp.status === 200 || resp.status === 201)) {
    showResult('ok', 'Saved to your tracker.');
    els.saveBtn.textContent = 'Saved';
    return; // leave disabled to avoid a duplicate save
  } else if (resp && resp.status === 409) {
    showResult('info', 'Already saved — this posting is in your tracker.');
  } else if (resp && resp.status === 401) {
    show(els.authPrompt);
    showResult('err', 'Log in to the web app first, then try again.');
  } else {
    const detail = resp && resp.body && resp.body.message ? ` (${resp.body.message})` : '';
    showResult('err', `Could not save${detail}. Is the backend running?`);
  }

  els.saveBtn.disabled = false;
  els.saveBtn.textContent = 'Save to tracker';
});

// Kick things off.
init();
