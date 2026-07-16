# archival-worker

Plain Spring Boot worker (port **8084**) that keeps the board tidy by archiving old completed
application tiles.

## What it does

An hourly `@Scheduled` sweep (`job/ArchivalJob`, cron overridable via `ARCHIVAL_CRON`) runs
against the shared Mongo `tracker` database. It finds tiles in the `applications` collection
where:

- `bucket == "complete"`, **and**
- `archived == false`, **and**
- `completedAt < now - ARCHIVE_AFTER_DAYS days` (default **7**)

and bulk-updates them (`MongoTemplate.updateMulti`) to `archived = true`, `bucket = "archived"`,
logging how many were archived.

The `applications` collection is **owned by application-service** (see `CONTRACT.md`); this
worker only flips the archival flags. Bucket wire values are the lowercase strings
`applied | in_progress | complete | archived`.

## Config

| Env var | Default | Meaning |
|---|---|---|
| `SERVER_PORT` | `8084` | Actuator/health port |
| `SPRING_DATA_MONGODB_URI` | `mongodb://localhost:27017/tracker` | Mongo connection |
| `ARCHIVE_AFTER_DAYS` | `7` | Retention window before archiving |
| `ARCHIVAL_CRON` | `0 0 * * * *` | 6-field Spring cron for the sweep (hourly) |

Health: `GET /actuator/health`.

## Run locally

```bash
cp .env.example .env      # then export/source it
mvn spring-boot:run
```
