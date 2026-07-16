# Job Application Tracker

A full-stack app + Chrome extension for tracking job applications across portals.
**No scraping, no third-party portal credentials.** The user updates each
application's status manually. The extension only *captures* a posting (pre-filling
title/company from the page's Open Graph/meta tags) and *detects* new portals.

## Architecture

```
                         ┌───────────────────────────┐
  Browser Extension ───► │      api-gateway (8080)    │  JWT validation,
  Web Frontend (5173) ─► │  routing · rate limit · TLS│  rate limiting
                         └────────────┬──────────────┘
              ┌───────────────────────┼────────────────────────┐
              ▼                       ▼                         ▼
     auth-service (8081)   application-service (8082)   portal-registry (8083)
        Postgres              Mongo · Redis · S3               Mongo
                                     ▲
                              archival-worker (8084)  @Scheduled hourly → Mongo
```

| Service                   | Port | Store              | Responsibility                                   |
|---------------------------|------|--------------------|--------------------------------------------------|
| api-gateway               | 8080 | –                  | Routing, JWT validation, rate limiting, TLS      |
| auth-service              | 8081 | PostgreSQL         | App accounts, signup/login, JWT + refresh tokens |
| application-service       | 8082 | Mongo · Redis · S3 | Tiles CRUD, board, sidebar cache, S3 documents   |
| portal-registry-service   | 8083 | MongoDB            | Known-portal catalog (list/add), extension sync  |
| archival-worker           | 8084 | MongoDB            | Hourly job: auto-archive completed tiles > 7d    |

> **document-service** is folded into `application-service` for the MVP (spec-allowed).
> All S3 logic is isolated in the `com.tracker.application.document` package so it can
> be extracted into its own service later without touching the domain code.

## Two identifier fields (do not conflate)

- **`applicationId`** — internal UUID generated **server-side** at tile creation. Stable
  primary key. Used in **all** routes (`/applications/{applicationId}`). Never changes.
- **`externalJobId`** — the portal's own posting id (LinkedIn `currentJobId`, Greenhouse
  `gh_jid`, …). Nullable. Used only for display, back-linking, dedup. **Never for routing.**

## Run it locally

Prerequisites: Docker + Docker Compose. (JDK 17 + Node 20 only if running services/frontend outside Docker.)

```bash
cp .env.example .env                 # set JWT_SECRET (>= 32 bytes)
docker compose up --build            # infra + all 5 services
```

Then the frontend:

```bash
cd frontend
cp .env.example .env                 # VITE_API_BASE=http://localhost:8080
npm install
npm run dev                          # http://localhost:5173
```

Load the extension:

1. `chrome://extensions` → enable Developer mode → **Load unpacked** → select `/extension`.
2. After logging into the web app, the app pushes your JWT to the extension via
   `externally_connectable` (see the `TODO` for the login domain in `extension/manifest.json`).

## Everything talks through the gateway

All client traffic hits **`http://localhost:8080`**. The gateway validates the JWT and
forwards `X-User-Id` downstream. Services never trust a client-supplied userId.

- `POST /api/v1/auth/signup`, `/login`, `/refresh` → auth-service
- `/api/v1/applications/**`                        → application-service (JWT required)
- `/api/v1/portals/**`                             → portal-registry-service

See `CONTRACT.md` for the frozen cross-service contract (ports, env vars, JWT claims,
error envelope) that every service adheres to.

## Repo layout

```
job-application-tracker/
├── docker-compose.yml
├── CONTRACT.md
├── .env.example
├── infra/localstack-init/        # creates the S3 bucket on startup
├── services/
│   ├── api-gateway/
│   ├── auth-service/
│   ├── application-service/      # + folded document (S3) package
│   ├── portal-registry-service/
│   └── archival-worker/
├── frontend/                     # React 18 + Vite
└── extension/                    # Chrome Manifest V3, vanilla JS
```

## What is intentionally NOT here

No web scraping, no credential vaulting, no automated portal login. The extension reads
only visible page metadata (OG/meta tags) — never cookies or credentials.
