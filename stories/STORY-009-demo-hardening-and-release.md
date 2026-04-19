# STORY-009 — Demo Hardening & v2 Release

**Type**: chore
**Status**: todo
**Branch**: `chore/demo-hardening-and-v2-release`
**Depends on**: STORY-002 through STORY-008
**Estimated PR size**: ~200 lines (mostly config, seed data, docs)

---

## Context

All v2 feature modules are merged to `develop` and running on staging. This story prepares the jury demo: a seeded demo account, a rehearsed golden-path script, updated README/DEPLOYMENT docs, a production deploy, and the `v2.0.0` git tag.

---

## What Needs to Be Done

### Step 1 — Demo seed data (single owner)
This story is the **single owner** of all demo seeding (previously STORY-006 also declared a `V004__seed_demo_portfolio.sql` — that has been removed to avoid duplication).

Use a `@Profile("demo")` `ApplicationRunner` bean (preferred over Flyway for demo data, since it can insert BCrypt-hashed passwords without hand-computing hashes in SQL). Located under `backend/src/main/java/com/bloomfield/terminal/demo/DemoSeedRunner.java`, guarded by `@Profile("demo")` so it runs only when `SPRING_PROFILES_ACTIVE=demo`.

Seeded data:
- 1 ADMIN user: `admin@altaryslabs.com` / (strong password, documented in README)
- 1 ANALYST user: `analyst@demo.bloomfield` / (strong password)
- 1 VIEWER user: `viewer@demo.bloomfield` / (strong password)
- ANALYST portfolio with 6 realistic positions across sectors (Finance, Télécoms, Énergie, Agriculture)
- ANALYST has 3 pre-configured alert rules (one ABOVE, one BELOW, one CROSSES_UP) — positioned so at least one fires during a 10-minute demo window

Idempotent: skip inserts if rows already exist (re-runnable on container restart).

### Step 2 — Golden-path demo script
`docs/demo-script.md` (private, not in `_kb_/`):
1. Open landing → Login as ANALYST
2. Terminal loads → highlight live quotes, indices, order book
3. Switch to Portfolio tab → show live valuation, P&L coloring, total
4. Switch to Alerts tab → wait for a scripted alert to fire, show toast + event
5. Login as ADMIN in a second window → show user management
6. Show the architecture: open `http://staging.../actuator/modulith` (if Modulith actuator is enabled)

Time budget: 8 minutes. Include fallback lines if something fails ("these are simulated tickers, the architecture behaves identically with a real feed").

### Step 3 — Enable Modulith actuator (recommended for RFP)
`spring-modulith-actuator` dep + enable `management.endpoints.web.exposure.include: modulith, health`. Gives a live module map at `/actuator/modulith` — useful visual for the jury.

### Step 4 — Production hardening checks
- JWT secret is injected via env var in production, not committed
- CORS `allowed-origins` includes only the prod domain (already set — verify). With cookie auth (STORY-005), ensure `allowCredentials=true` is paired with exact origins in prod — switch from `allowedOriginPatterns` to `allowedOrigins` on prod profile and fail-fast on startup if wildcards detected.
- Refresh-token cookie flags in prod: `HttpOnly; Secure; SameSite=Strict; Path=/auth`. Add a smoke test that asserts these on the `Set-Cookie` header after `/auth/login`.
- Spring Security: disable the H2 console (none enabled, verify), disable stack traces in error responses
- DB connection pool sized (`spring.datasource.hikari.maximum-pool-size`) — 10 is plenty for a demo
- Log level `INFO` in prod, `DEBUG` only for `com.bloomfield` on staging
- Health check endpoint (`/api/health` or `/actuator/health`) returns DB status

### Step 5 — Update READMEs
- Root `README.md` — v2 architecture diagram, how to run locally (`docker compose up && ./gradlew bootRun`), link to each STORY doc
- `DEPLOYMENT_GUIDE.md` — updated env vars, TimescaleDB notes, JWT secret generation command

### Step 6 — Release PR `develop → main`
- Title: `Release v2.0.0 — Bloomfield Terminal v2`
- Body: summary of STORY-002 through STORY-008 + rollback plan (revert merge commit + Coolify redeploys previous state; tag `v1.0.0-prototype` remains the escape hatch)

### Step 7 — Tag
```bash
git tag -a v2.0.0 -m "Bloomfield Terminal v2 — Modulith, auth, portfolio, alerts, TimescaleDB"
git push origin v2.0.0
```

### Step 8 — Post-deploy verification
- All three demo accounts log in successfully
- Market data stream connects within 5s of login
- Portfolio totals non-zero, P&L updating
- Alerts scripted rule fires within 10 min of start
- Coolify logs clean for 15 min post-deploy

---

## Acceptance Criteria

- [ ] Demo profile seeds three users with portfolios and alerts
- [ ] `docs/demo-script.md` written and rehearsed once end-to-end
- [ ] Release PR green, merged as merge commit (not squash, preserves feature PR history)
- [ ] `v2.0.0` tag pushed
- [ ] Production smoke test checklist 100% complete
- [ ] Rollback path tested on staging (revert merge commit, app returns to previous state)

---

## Out of Scope

- Load testing / performance benchmarks
- CDN / edge caching
- Sentry / error monitoring integration
- Uptime monitoring (external)

---

## Related Files

- `backend/src/main/resources/db/migration/demo/**` or `@Profile("demo")` seed bean (new)
- `docs/demo-script.md` (new)
- `README.md` (update)
- `DEPLOYMENT_GUIDE.md` (update)
- `backend/src/main/resources/application-prod.yml` (hardening)
