# Bloomfield Terminal — Deployment Guide

This guide covers the v2 release. It focuses on the environment variables, secrets generation, and the Flyway migrations that must be present on the target database (TimescaleDB).

Companion docs:
- [`_kb_/multi-environment-setup.md`](_kb_/multi-environment-setup.md) — staging vs. prod branch model, Coolify profiles.
- [`_kb_/coolify-feature-preview-playbook.md`](_kb_/coolify-feature-preview-playbook.md) — feature-branch previews.
- [`docs/demo-script.md`](docs/demo-script.md) — what to show on the demo.

---

## 1. Target profile

| Profile    | Activation                         | Purpose                                                              |
|------------|------------------------------------|----------------------------------------------------------------------|
| `default`  | no `SPRING_PROFILES_ACTIVE`        | Local dev against `docker compose up -d postgres`.                   |
| `demo`     | `SPRING_PROFILES_ACTIVE=demo`      | Local rehearsal of the jury demo. Seeds three users + portfolio + alerts. |
| `staging`  | `SPRING_PROFILES_ACTIVE=staging`   | Staging deploy on Coolify (`staging-bf-terminal.altaryslabs.com`).   |
| `prod`     | `SPRING_PROFILES_ACTIVE=prod`      | Production deploy (`bloomfield-intelligence.altaryslabs.com`). Strict CORS, hardened cookies. |

`prod` applies the strictest rules:
- `app.cors.allowed-origins` uses `allowedOrigins` (not `allowedOriginPatterns`). A wildcard (`*`) in the list causes startup to fail-fast (see `shared/CorsConfig.java`).
- Refresh-token cookie: `HttpOnly; Secure; SameSite=Strict; Path=/auth; Max-Age=7d`.
- `server.error.include-stacktrace=never`, `server.error.include-message=never`.
- Hikari pool capped at 10 connections.
- `/actuator/health` details only visible to authenticated users.

---

## 2. Required environment variables

| Variable                           | Required where        | Notes                                                                 |
|------------------------------------|-----------------------|-----------------------------------------------------------------------|
| `SPRING_DATASOURCE_URL`            | staging / prod        | `jdbc:postgresql://<host>:5432/bloomfield`                            |
| `SPRING_DATASOURCE_USERNAME`       | staging / prod        |                                                                       |
| `SPRING_DATASOURCE_PASSWORD`       | staging / prod        | Coolify secret                                                        |
| `APP_JWT_SECRET`                   | **always**            | 32-byte hex (see below). Never commit. Rotated invalidates all tokens.|
| `SPRING_PROFILES_ACTIVE`           | staging / prod / demo | `staging`, `prod`, or `demo`                                          |
| `DEMO_USERS_ADMIN_PASSWORD`        | demo only             | Overrides default seed password.                                      |
| `DEMO_USERS_ANALYST_PASSWORD`      | demo only             | Idem.                                                                 |
| `DEMO_USERS_VIEWER_PASSWORD`       | demo only             | Idem.                                                                 |

### Generate a strong JWT secret

```bash
openssl rand -hex 32
```

Paste the output into Coolify as `APP_JWT_SECRET` for the target service. The secret must be at least 32 bytes to be accepted by the `HS256` signer (`SecurityConfig.jwtDecoder`).

---

## 3. Database (TimescaleDB)

The app targets PostgreSQL 17 with the TimescaleDB extension. Flyway applies the following migrations in order on startup:

| Version | File                                       | Notes                                                                 |
|---------|--------------------------------------------|-----------------------------------------------------------------------|
| V001    | `V001__baseline.sql`                        | Installs `CREATE EXTENSION IF NOT EXISTS timescaledb;` baseline.      |
| V002    | `V002__user_module.sql`                     | Users + refresh tokens.                                               |
| V003    | `V003__portfolio_module.sql`                | Portfolios, positions, trades.                                        |
| V004    | `V004__alerts_module.sql`                   | Alert rules + alert events.                                           |
| V005    | `V005__spring_modulith_event_publication.sql` | Spring Modulith event table (used in `completion-mode: delete`).    |
| V006    | `V006__ohlcv_hypertable.sql`                | `ohlcv` hypertable (TimescaleDB) for candle history (STORY-008).      |

If you point the app at a vanilla PostgreSQL (no TimescaleDB), V001 fails. Use the `timescale/timescaledb:latest-pg17` image in Docker Compose and in Coolify.

Demo seeding uses a `@Profile("demo")` `ApplicationRunner` rather than Flyway, so that BCrypt-hashed passwords do not have to be precomputed in SQL.

---

## 4. Deploy on Coolify

1. Push branch to `develop` or `main` (Coolify watches both — staging and prod respectively).
2. Confirm the env vars on the target service (see table above).
3. Trigger redeploy from the Coolify UI or let the watcher pick up the push.
4. Verify the smoke endpoints within 5 minutes:
   ```
   GET https://<host>/actuator/health          -> {"status":"UP"}
   GET https://<host>/actuator/modulith         -> module graph JSON
   POST https://<host>/auth/login                -> 200 with Set-Cookie refresh_token
   ```
5. Check Coolify logs for 15 minutes — no stack traces, no repeated WARNs.

Rollback: revert the merge commit on `main`; Coolify redeploys the previous image automatically.

---

## 5. Local demo rehearsal

```bash
docker compose up -d postgres
cd backend
SPRING_PROFILES_ACTIVE=demo \
APP_JWT_SECRET=$(openssl rand -hex 32) \
./gradlew bootRun
```

Frontend:
```bash
cd frontend
pnpm install
pnpm dev
```

Log in as `analyst@demo.bloomfield` / `ChangeMe!Analyst2026` and follow [`docs/demo-script.md`](docs/demo-script.md).
