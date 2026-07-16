# api-gateway

Reactive edge gateway (Spring Cloud Gateway on WebFlux, port **8080**) for the
job-application-tracker. It is the single entry point for the Vite frontend and the browser
extension; everything behind it speaks plain HTTP on the internal network.

## Routing

All routes are env-driven (defaults for local dev in parentheses):

| Client path | Target env var | Default |
|---|---|---|
| `/api/v1/auth/**` | `AUTH_SERVICE_URI` | `http://localhost:8081` |
| `/api/v1/applications/**` | `APPLICATION_SERVICE_URI` | `http://localhost:8082` |
| `/api/v1/portals/**` | `PORTAL_SERVICE_URI` | `http://localhost:8083` |

Routes are declared in `src/main/resources/application.yml`.

## JWT validation & the X-User-Id trust model

A global filter (`security/JwtValidationFilter`) runs on **every** request except the public
`/api/v1/auth/**` routes and `/actuator/**`.

- It validates the `Authorization: Bearer <token>` HS256 token using the shared `JWT_SECRET`
  (see `CONTRACT.md`). Invalid/missing/expired tokens get a `401` JSON body
  `{ "status": 401, "error": "Unauthorized", "message": "..." }`.
- On success it injects `X-User-Id: <token subject>` into the forwarded request.
- **Any client-supplied `X-User-Id` is stripped first, on every request** (including auth
  routes). So the only `X-User-Id` downstream services ever see is one this gateway derived
  from a verified token. That is the whole trust model: downstream services may trust
  `X-User-Id` *because it can only originate here*. (application-service additionally
  re-validates the bearer token itself in the scaffold — defense in depth.)
- Auth routes pass through with no token so login/refresh can mint tokens.

## Rate limiting

Uses Spring Cloud Gateway's `RequestRateLimiter` backed by `RedisRateLimiter` (Redis via
`SPRING_DATA_REDIS_HOST` / `SPRING_DATA_REDIS_PORT`). The key is the resolved user:
`config/RateLimitConfig#userKeyResolver` keys by the trusted `X-User-Id`, falling back to the
client IP for the (userless) auth routes.

The budget comes from a single env var `RATE_LIMIT_REQUESTS_PER_MINUTE` (default **120**).
`RateLimitConfig` derives the token-bucket settings: `replenishRate = RPM / 60` (≈ 2/s),
`burstCapacity = RPM` (120). Exceeding the limit yields a gateway `429`.

## CORS

Global CORS (`config/CorsConfig`) allows the Vite frontend origin
(`CORS_ALLOWED_ORIGIN`, default `http://localhost:5173`) and, via `allowedOriginPatterns("*")`,
browser-extension (`chrome-extension://`) origins. Methods `GET/POST/PATCH/DELETE/OPTIONS`, all
headers (incl. `Authorization`), credentials allowed. **The `"*"` pattern is a scaffold
convenience — restrict it to the published extension id(s) in production.**

## TLS termination (production)

In production, **TLS terminates at this gateway**; internal services stay on plain HTTP. No
certificates are generated or committed here. To enable, provide a keystore via env
(`TLS_KEYSTORE_PATH` / `TLS_KEYSTORE_PASSWORD`) and uncomment the `server.ssl.*` block in
`application.yml` (switching the port to 8443). For local dev the gateway stays on **HTTP:8080**.

## Run locally

```bash
cp .env.example .env      # then export/source it
mvn spring-boot:run
```

Requires Redis on `localhost:6379` and the downstream services running (or reachable at the
configured URIs).
