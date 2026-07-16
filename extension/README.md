# Job Application Tracker — Chrome Extension (Manifest V3)

Vanilla JS, no framework. This extension **captures** a job posting (pre-filling
title/company from the page's visible Open Graph / meta tags) and **detects new
portals**. It talks to the backend only through the api-gateway at
`http://localhost:8080/api/v1`.

> **No scraping of credentials.** The content script reads only *visible* page
> metadata — Open Graph tags, `<meta>` tags, `<title>`, and a few generic DOM
> selectors as fallbacks. It never reads cookies, `localStorage`,
> `sessionStorage`, form values, or any portal login state.

## Files

| File | Role |
|------|------|
| `manifest.json` | MV3 manifest: permissions, content script, popup, auth bridge. |
| `background.js` | Service worker: portal sync (every 6h), URL classification, authed backend calls, JWT bridge. |
| `content.js` | Reads visible page metadata; extracts an `externalJobId` from the URL. |
| `popup.html` / `popup.js` | Capture form + "new portal detected" banner. |

## Load unpacked

1. Open `chrome://extensions`.
2. Enable **Developer mode** (top-right).
3. Click **Load unpacked** and select this `extension/` folder.
4. (Optional) Pin the extension so its toolbar icon/badge is visible. The badge
   shows `NEW` on a page whose portal isn't registered yet, and `•` on a known
   job page.

Icons are omitted for local dev (see the comment in `manifest.json`). Add PNGs
and an `icons` key before publishing.

## Auth bridge (how the extension gets your JWT)

The extension never logs you in and never stores portal credentials. It reuses
the **web app's** session:

1. You log into the web app (local dev: `http://localhost:5173`).
2. After login, the web app pushes the access token to the extension:

   ```js
   // In the web app, after a successful login:
   const EXTENSION_ID = 'TODO: your unpacked/published extension id';
   chrome.runtime.sendMessage(EXTENSION_ID, { type: 'SET_JWT', jwt: accessToken });
   ```

   This works because `manifest.json` lists the web-app origin under
   `externally_connectable.matches`. **TODO:** replace both the extension id
   above and the `externally_connectable` origin (`http://localhost:5173/*`)
   with your real published extension id and deployed web-app origin.

3. `background.js` stores the JWT in `chrome.storage.local` under the key `jwt`
   and attaches it as `Authorization: Bearer <jwt>` on authed calls.

If no JWT is present, the popup shows a **"Log in to the web app first"** prompt
instead of failing silently. Access tokens are short-lived (15 min per the
contract); re-pushing after each login keeps the extension current.

## What it calls

- `GET /portals` — cached locally as `knownPortals` (domains + display-name map),
  refreshed on install/startup and every 6 hours via `chrome.alarms`.
- `POST /portals` `{ domain, displayName }` — from the "Add portal" button when a
  new portal is detected. 409 means it's already registered.
- `POST /applications` `{ externalJobId?, portalId, portalName, jobUrl, jobTitle,
  company, notes?, descriptionSnapshot? }` — from "Save to tracker". 409 means the
  posting is already in your tracker.

## URL classification (`classifyUrl`)

`known-portal` / `new-portal` / `ignore`, decided from the cached portal list plus
a job-page heuristic:

- **Job page** if the path contains `/jobs/`, `/job/`, `/careers/`, `/viewjob`,
  or the URL has a known job-id query param (`currentJobId`, `jobId`, `gh_jid`,
  `jk`, `postingId`).
- **known-portal**: job page **and** host (minus `www.`) suffix-matches a
  registered portal domain.
- **new-portal**: job page but host is not registered → offer to add it.
- **ignore**: not a job page.

`externalJobId` is pulled from the URL only (query params
`currentJobId` → `jobId` → `gh_jid` → `jk` → `id` → `postingId`, then common
path patterns like `/jobs/{id}`), and returns `null` when nothing matches. It is
shown read-only and used for display/back-linking/dedup — **never for routing**.
