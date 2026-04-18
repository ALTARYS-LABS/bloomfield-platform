# STORY-002 — Persistence Layer & Spring Modulith Skeleton

**Type**: feat
**Status**: todo
**Branch**: `feat/persistence-and-modulith-skeleton`
**Estimated PR size**: ~300 lines

---

## Context

The v1 prototype keeps all market data in-memory (`ConcurrentHashMap` inside `MarketDataSimulator`) and has no persistence layer, no modular boundaries, and no tests. v2 requires Spring Modulith with module boundaries, Spring Data JDBC (chosen over JPA), Flyway for all migrations, and TestContainers-backed tests.

This story is pure foundation — **no user-visible behavior change**. It introduces the infrastructure that every subsequent story depends on.

---

## What Needs to Be Done

### Step 1 — Add dependencies to `backend/build.gradle.kts`
- `spring-boot-starter-data-jdbc`
- `spring-boot-starter-validation`
- `spring-modulith-starter-core`
- `spring-modulith-starter-jdbc`
- `org.flywaydb:flyway-core` + `flyway-database-postgresql`
- `org.postgresql:postgresql`
- Test: `spring-boot-starter-test`, `spring-modulith-starter-test`, `org.testcontainers:postgresql`, `org.testcontainers:junit-jupiter`

### Step 2 — Extend `docker-compose.yml`
Add a `postgres` service using the TimescaleDB image (`timescale/timescaledb:latest-pg17` or latest compatible). No hypertables created yet — that is STORY-008. Using the Timescale image now avoids a later image swap.

Expose env: `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` in the backend service.

### Step 3 — Restructure packages into modules
Move existing code into a `marketdata` module, create a `shared` module, and prepare the top-level application package for future modules (`user`, `portfolio`, `alerts` will come in later stories).

```
com.bloomfield.terminal
├── TerminalApplication.java
├── shared/           # package-info.java with @ApplicationModule
└── marketdata/       # package-info.java with @ApplicationModule
    ├── api/          # public interfaces + DTOs (named interface for Modulith)
    ├── config/       # MarketIndicesProperties, WebSocketConfig
    ├── web/          # HistoryController (renamed controller package)
    ├── domain/       # Quote, TickerState, OrderBookEntry, MarketIndex (former model/)
    └── internal/     # MarketDataSimulator (future: behind interface in STORY-003)
```

Config classes stay near their module but `CorsConfig`/`CorsProperties` belong in `shared` (cross-cutting).

### Step 4 — First Flyway migration
Create `backend/src/main/resources/db/migration/V001__baseline.sql` — empty or with a single `-- baseline` comment. Establishes Flyway's schema history table on startup.

### Step 5 — Configure `application.yml`
Add `spring.datasource.*`, `spring.flyway.enabled: true`, and `spring.jpa.hibernate.ddl-auto` is NOT set (JPA is not used). Use `spring.sql.init.mode: never`.

### Step 6 — First tests (seed `src/test/`)
- `ApplicationModulesTest` — calls `ApplicationModules.of(TerminalApplication.class).verify()` (enforces module boundaries)
- `TerminalApplicationIntegrationTest` — `@SpringBootTest` with `@Testcontainers` booting Postgres, asserts context loads

### Step 7 — Update CLAUDE.md rule #1
Critical rule #1 currently says *"Every database query MUST include tenant isolation context"*. Since v2 is explicitly single-tenant (one company), this rule is obsolete. Replace it with:

> **1. Data access goes through Spring Data JDBC repositories — never raw `JdbcTemplate` outside `internal/` packages. No JPA.**

---

## Acceptance Criteria

- [ ] `docker compose up -d` brings up Postgres healthy
- [ ] `./gradlew bootRun` starts the app against that Postgres, Flyway migration `V001` applied, schema history table present
- [ ] `./gradlew test` green — at minimum `ApplicationModulesTest` and one integration test with TestContainers pass
- [ ] Package structure matches the tree above; `@ApplicationModule` annotations present
- [ ] No behavior change visible to the frontend — v1 UI still works end to end against the refactored backend
- [ ] `./gradlew spotlessApply` clean

---

## Out of Scope (deferred to later stories)

- Extracting `MarketDataProvider` interface (STORY-003)
- Expanding from 10 to ~45 tickers (STORY-003)
- Any domain persistence tables — market data remains in memory for now

---

## Related Files

- `backend/build.gradle.kts`
- `backend/src/main/java/com/bloomfield/terminal/**` (all existing files move)
- `backend/src/main/resources/application.yml`
- `backend/src/main/resources/db/migration/V001__baseline.sql` (new)
- `backend/src/test/java/com/bloomfield/terminal/ApplicationModulesTest.java` (new)
- `docker-compose.yml`
- `CLAUDE.md` (rule #1 updated)
