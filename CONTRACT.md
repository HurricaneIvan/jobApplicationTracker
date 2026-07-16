# Cross-service contract (FROZEN)

Every service adheres to this. Changing it means changing every service.

## Ports
| Service | Port |
|---|---|
| api-gateway | 8080 |
| auth-service | 8081 |
| application-service | 8082 |
| portal-registry-service | 8083 |
| archival-worker | 8084 |
| Postgres | 5432 · db `auth` · user/pass `tracker`/`tracker` |
| Mongo | 27017 · db `tracker` |
| Redis | 6379 |
| LocalStack S3 | 4566 · bucket `application-copies` |

## JWT
- Algorithm **HS256**, shared secret in env `JWT_SECRET` (>= 32 bytes).
- Issued by auth-service. Validated by gateway, application-service, portal-registry-service.
- Access token TTL 15 min; refresh token TTL 14 days (opaque, stored in Postgres).
- **Claims:** `sub` = userId (UUID string), `email`, `type` = `"access"`, plus `iat`/`exp`.
- The gateway validates the token and injects header **`X-User-Id`** downstream.
  Downstream services read userId from `X-User-Id` **or** re-validate the JWT directly
  (application-service does both — trust `X-User-Id` only when present *and* the request
  arrived via the gateway; in the scaffold it re-validates the bearer token itself).

## Error envelope (global exception handlers return this shape)
```json
{ "timestamp": "2026-07-16T12:00:00Z", "status": 409, "error": "Conflict",
  "message": "Application already exists", "path": "/api/v1/applications",
  "data": { } }
```
- Dedup collision → **409** with the existing tile in `data`.
- Not found → **404**. Validation → **400**. Auth → **401**. Rate limit → **429**.

## Env var names (identical across services where shared)
`SERVER_PORT`, `JWT_SECRET`, `JWT_ACCESS_TTL_SECONDS`, `JWT_REFRESH_TTL_SECONDS`,
`SPRING_DATA_MONGODB_URI`, `SPRING_DATASOURCE_URL/USERNAME/PASSWORD`,
`SPRING_DATA_REDIS_HOST/PORT`, `AWS_REGION`, `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`,
`S3_ENDPOINT`, `S3_BUCKET`, `S3_PRESIGN_TTL_SECONDS`, `ARCHIVE_AFTER_DAYS`.

## Mongo `tracker` database — shared collections
- `applications` (owned by application-service; read/written by archival-worker)
- `portals` (owned by portal-registry-service)

## Base paths (as seen by clients, through the gateway)
- `/api/v1/auth/**`, `/api/v1/applications/**`, `/api/v1/portals/**`
