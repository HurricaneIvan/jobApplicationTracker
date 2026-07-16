/**
 * Job Application Tracker — content script.
 *
 * Reads ONLY visible page metadata to help pre-fill the capture form:
 *   - Open Graph tags (og:title, og:site_name, og:description)
 *   - <meta name="..."> tags
 *   - <title>
 *   - generic DOM fallbacks (h1, [class*=title], [class*=company])
 *
 * It NEVER touches cookies, localStorage, sessionStorage, credentials, or any
 * authenticated/hidden state. Everything it reads is already visible on the page.
 *
 * It responds to a GET_PAGE_INFO message with the scraped fields.
 */

'use strict';

/** Read the content of a <meta property="og:*"> tag. */
function metaProperty(prop) {
  const el = document.querySelector(`meta[property="${prop}"]`);
  return el ? (el.getAttribute('content') || '').trim() : '';
}

/** Read the content of a <meta name="*"> tag. */
function metaName(name) {
  const el = document.querySelector(`meta[name="${name}"]`);
  return el ? (el.getAttribute('content') || '').trim() : '';
}

/** First non-empty text from a list of CSS selectors. */
function firstText(selectors) {
  for (const sel of selectors) {
    const el = document.querySelector(sel);
    if (el && el.textContent && el.textContent.trim()) {
      return el.textContent.trim().replace(/\s+/g, ' ');
    }
  }
  return '';
}

/**
 * Extract the portal's own posting id from the URL, if present.
 * Checks query params in priority order, then common path patterns.
 * @returns {string|null}
 */
function extractExternalJobId(url) {
  let u;
  try {
    u = new URL(url);
  } catch {
    return null;
  }

  // 1) Query params, in priority order.
  const paramOrder = ['currentJobId', 'jobId', 'gh_jid', 'jk', 'id', 'postingId'];
  for (const key of paramOrder) {
    // case-insensitive lookup
    for (const [k, v] of u.searchParams.entries()) {
      if (k.toLowerCase() === key.toLowerCase() && v) {
        return v;
      }
    }
  }

  // 2) Common path patterns: /jobs/{id}, /job/{id}, greenhouse /jobs/{numericId}, /viewjob/{id}
  const path = u.pathname;
  const patterns = [
    /\/jobs\/([A-Za-z0-9\-_]+)/i,
    /\/job\/([A-Za-z0-9\-_]+)/i,
    /\/viewjob\/([A-Za-z0-9\-_]+)/i,
    /\/postings\/([A-Za-z0-9\-_]+)/i // Lever: /postings/{uuid}
  ];
  for (const re of patterns) {
    const m = path.match(re);
    if (m && m[1]) return m[1];
  }

  return null;
}

/** Best-effort job title from metadata, then DOM. */
function extractJobTitle() {
  return (
    metaProperty('og:title') ||
    metaName('title') ||
    firstText(['h1', '[class*="job-title"]', '[class*="jobtitle"]', '[class*="title"]']) ||
    (document.title || '').trim()
  );
}

/** Best-effort company / site name from metadata, then DOM. */
function extractCompany() {
  return (
    metaProperty('og:site_name') ||
    firstText([
      '[class*="company-name"]',
      '[class*="companyName"]',
      '[class*="employer"]',
      '[class*="company"]'
    ]) ||
    ''
  );
}

/** Best-effort description snapshot from metadata. */
function extractDescription() {
  return (
    metaProperty('og:description') ||
    metaName('description') ||
    ''
  ).slice(0, 2000); // cap the snapshot size
}

/** Collect everything the popup needs. */
function collectPageInfo() {
  const jobUrl = window.location.href;
  return {
    jobTitle: extractJobTitle(),
    company: extractCompany(),
    description: extractDescription(),
    jobUrl,
    externalJobId: extractExternalJobId(jobUrl)
  };
}

chrome.runtime.onMessage.addListener((msg, sender, sendResponse) => {
  if (msg && msg.type === 'GET_PAGE_INFO') {
    try {
      sendResponse(collectPageInfo());
    } catch (err) {
      sendResponse({ error: String(err) });
    }
  }
  // synchronous response; no need to return true
});
