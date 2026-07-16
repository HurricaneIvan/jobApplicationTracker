# Job Application Tracker — Frontend

React 18 + Vite web app for the Job Application Tracker. All traffic goes through
the API gateway (`VITE_API_BASE`, default `http://localhost:8080`) at base path
`/api/v1`.

## Run

```bash
cp .env.example .env      # VITE_API_BASE=http://localhost:8080
npm install
npm run dev               # http://localhost:5173
```

Scripts: `npm run dev`, `npm run build`, `npm run preview`.

## What's here

- `src/api/client.js` — centralized API client. Stores JWT tokens in
  `localStorage`, attaches `Authorization: Bearer`, wraps `fetch`, parses the
  shared error envelope (surfaces 409 conflicts with the existing tile in
  `err.data`), and transparently retries once after refreshing the access token
  on a 401. On an unrecoverable 401 it clears tokens and fires the unauthorized
  handler so the app redirects to `/login`.
- `src/hooks/useAuth.jsx` — auth context (login / signup / logout, current user).
- `src/pages/AuthPage.jsx` — combined login/signup screen.
- `src/pages/BoardPage.jsx` — four-column board (Applied · In Progress ·
  Complete · Archived). Tiles are auto-placed by `bucket`. Includes a portal
  filter (from `GET /portals`), an inline "New application" form, and refetches
  the board after every mutation so tiles move columns. The right sidebar is
  collapsible.
- `src/components/Tile.jsx` — a card: title, company, portal badge, both id
  fields (**App ID** = `applicationId`, used for all routing; **Job ID** =
  `externalJobId`, display/back-link only, shows `—` when null), a status
  dropdown (PATCH status), editable notes (PATCH notes on save), a
  document button (presigned PUT upload / presigned GET download), a link to the
  posting, and delete.
- `src/components/Sidebar.jsx` — collapsible flat list of **non-archived** tiles
  via `GET /applications/sidebar`, with sort (dateApplied / title / status) and
  an asc/desc toggle. Archived tiles never appear here.

## Identifiers

`applicationId` is the internal id used for **all** routing, keys, and API calls.
`externalJobId` is the portal's posting id — nullable, shown for reference and
used only for the back-link. Never route on it.
