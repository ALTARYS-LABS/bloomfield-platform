# Story Index: Bloomfield Terminal

**Module**: bloomfield-terminal
**Total stories**: 12
**Decomposed from**: `proto_v2_en.md` (Bloomfield Terminal v2 advanced model)
**Product context**: Bloomfield Intelligence RFP AO_BI_2026_001 — BRVM real-time terminal (groupement IBEMS + ALTARYS LABS)
**Date**: 18/04/2026
**Last updated**: 21/04/2026 (STORY-012 added: recalibrate seed and static data to real BRVM order of magnitude — queued for the release after v2.0.0)

---

## Dependency Graph

```
STORY-001 (Enforce Gitflow via Branch Protection) — standalone, infra
                                                   
STORY-002 (Persistence Layer & Modulith Skeleton)
    ├── STORY-003 (MarketDataProvider Interface + 45 Tickers)
    │       ├── STORY-006 (Portfolio Module) ← also depends on STORY-004, STORY-005 (WS auth)
    │       ├── STORY-007 (Alerts Module) ← also depends on STORY-004, STORY-005 (WS auth); introduces marketdata.api.QuoteTick
    │       └── STORY-008 (TimescaleDB Hypertable + OHLCV API) ← also depends on STORY-007 (consumes QuoteTick)
    │               └── STORY-010 (Sikafinance Historical Adapter) ← also depends on STORY-003
    └── STORY-004 (User Module — Backend Auth / JWT)
            └── STORY-005 (User Module — Frontend Login + refresh-token cookie + STOMP auth)

STORY-009 (Demo Hardening & v2 Release) ← depends on STORY-002 through STORY-008, optionally STORY-010
```

---

## Story List

| # | Story ID | Title | Depends On | Complexity | Status |
|---|----------|-------|------------|------------|--------|
| 1 | STORY-001 | Enforce Gitflow via GitHub Branch Protection | None | S | 🔲 Not started |
| 2 | STORY-002 | Persistence Layer & Spring Modulith Skeleton | None | M | 🔲 Not started |
| 3 | STORY-003 | `MarketDataProvider` Interface & 45 BRVM Tickers | STORY-002 | M | 🔲 Not started |
| 4 | STORY-004 | User Module — Backend Auth (JWT) | STORY-002 | L | 🔲 Not started |
| 5 | STORY-005 | User Module — Frontend Login, Refresh-Token Cookie & STOMP Auth | STORY-004 | M | 🔲 Not started |
| 6 | STORY-006 | Portfolio Module (Positions, Real-Time Valuation, P&L) | STORY-003, STORY-004, STORY-005 | M | 🔲 Not started |
| 7 | STORY-007 | Alerts Module (Price Thresholds, WS Notifications, Offline Delivery) | STORY-003, STORY-004, STORY-005 | M | 🔲 Not started |
| 8 | STORY-008 | TimescaleDB Hypertable & OHLCV History API | STORY-003, STORY-007 | M | 🔲 Not started |
| 9 | STORY-009 | Demo Hardening & v2 Release | STORY-002–STORY-008 | S | 🔲 Not started |
| 10 | STORY-010 | Sikafinance Historical Adapter (real BRVM data) | STORY-003, STORY-008 | M | ✅ Merged (PR #49) |
| 11 | STORY-011 | Chart Window Label Honesty + Simulated Mode History Seeding | STORY-008, STORY-010 | S | 🔲 Not started |
| 12 | STORY-012 | Recalibrate Seed & Static Data to Real BRVM Order of Magnitude | STORY-003, STORY-009, STORY-010 | S | 🔲 Not started |

Complexity legend: **S** = ≤200 lines / ≤1 day · **M** = 200–400 lines / 1–2 days · **L** = 400–600 lines / 2–3 days (split if friction).

---

## Implementation Order

Recommended sequence, respecting the golden rule of short-lived branches (merge to `develop` within 1–2 days per story).

### Phase 1 — Foundation (Days 1–3)

1. **STORY-002** — Persistence & Modulith skeleton — *Every other v2 story depends on this. No behavior change visible.*
2. **STORY-003** — `MarketDataProvider` interface + 45 tickers — *Unblocks Portfolio, Alerts, and the TimescaleDB chart API.*

### Phase 2 — Auth (Days 4–7)

3. **STORY-004** — User module backend (JWT, Spring Security, Flyway migration, repository + TestContainers).
4. **STORY-005** — User module frontend (login page, protected routes, token handling, WS auth header).

### Phase 3 — Domain Modules (Days 8–14)

5. **STORY-006** — Portfolio module — *First demo-visible v2 feature after login.*
6. **STORY-007** — Alerts module — *Second demo-visible feature. Introduces Modulith events across modules.*
7. **STORY-008** — TimescaleDB hypertable + chart API — *Replaces the simulator-regenerated history.*
8. **STORY-010** — Sikafinance historical adapter — *Plugs real BRVM history behind the `MarketDataProvider` interface, cached into the hypertable. Optional but high-impact for the jury demo.*

### Phase 4 — Release (Days 15–21)

9. **STORY-009** — Demo hardening, seed data, release PR `develop → main`, tag `v2.0.0`.

### Not part of the v2 release

- **STORY-001** — Gitflow branch protection. Operational concern, can be done any time in parallel (requires GitHub UI access, not a code PR).

---

## Parallelization Notes

Within dependency constraints, these pairs can be developed in parallel:

- **STORY-001** ‖ anything — pure GitHub admin task, no code overlap
- **STORY-003** ‖ **STORY-004** — both depend only on STORY-002 and touch disjoint packages (`marketdata/` vs `user/`). Ideal for two-dev parallel work.
- **STORY-005** ‖ **STORY-008** — once STORY-004 is merged for auth + STORY-003 is merged for the interface, frontend login and the hypertable chart API can progress in parallel.
- **STORY-006** ‖ **STORY-007** — both depend on STORY-003 + STORY-004. Disjoint modules (`portfolio/` vs `alerts/`). Do together if two devs available.
- **STORY-008** ‖ **STORY-006/007** — TimescaleDB work only touches `marketdata/` internals and a new `CandleController`. Safe to land in any order relative to Portfolio/Alerts.
- **STORY-010** ‖ **STORY-006/007** — Sikafinance adapter lives in `marketdata/internal/` and uses the hypertable from STORY-008 as a cache. Independent of Portfolio/Alerts work.

---

## Key Decisions Locked (from user, 18/04/2026)

| Decision | Choice | Where applied |
|---|---|---|
| Persistence style | Spring Data JDBC (not JPA) | STORY-002 |
| Build tool | Gradle single-project (not Maven multi-module) | STORY-002 |
| Modular boundaries | Spring Modulith + `modules.verify()` only (no ArchUnit) | STORY-002, re-verified in every story |
| Cache / pub-sub | **No Redis** for v2 — add only when a concrete bottleneck appears | — (explicitly excluded) |
| Multi-tenancy | **Single-tenant** (one company) → CLAUDE.md rule #1 is updated in STORY-002 | STORY-002 |
| Time-series DB | TimescaleDB image adopted in STORY-002; hypertable created in STORY-008 | STORY-002, STORY-008 |
| Auth model | JWT (HMAC) + Spring Security `oauth2-resource-server`, 3 roles ADMIN/ANALYST/VIEWER | STORY-004 |
| Access token TTL | 15 min · Refresh token TTL: 7 days | STORY-004 |
| Currency | XOF only for v2 | STORY-006 |
| Alert semantics | One-shot rules (auto-disable after fire); no sliding windows | STORY-007 |

---

## Flyway Migration Version Map

Versions assigned by implementation order. No gaps allowed.

| Version | Story | Migration |
|---------|-------|-----------|
| V001 | STORY-002 | `baseline` (empty — seeds Flyway history table) |
| V002 | STORY-004 | `user_module` (users, roles, user_roles, refresh_tokens) |
| V003 | STORY-006 | `portfolio_module` (portfolios, positions, trades) |
| V004 | STORY-007 | `alerts_module` (alert_rules, alert_events) |
| V005 | STORY-008 | `ohlcv_hypertable` (TimescaleDB extension + ohlcv + continuous aggregates) |

> Demo seed data (users + portfolios + alert rules) is **not a Flyway migration**. It is an `@Profile("demo")` `ApplicationRunner` owned by STORY-009 so it ships only when `SPRING_PROFILES_ACTIVE=demo`. Previously a V004 seed migration under STORY-006 was planned; it was removed to keep demo data out of the deterministic migration chain.

---

## PR Size Targets

Per `standards/git-workflow.md`: ideal < 200 lines, acceptable 200–400, split above 400. Estimated sizes from each story:

| Story | Estimated PR size | Notes |
|---|---|---|
| STORY-002 | ~300 | Config + package restructure + first test |
| STORY-003 | ~400 | Interface extraction + 45-ticker seed + tests |
| STORY-004 | ~500 ⚠️ | **Split expected**: (a) JWT + endpoints, (b) admin endpoints + tests |
| STORY-005 | ~500 ⚠️ | Scope expanded: frontend auth + backend refresh-token cookie + STOMP ChannelInterceptor (carry-over from STORY-004 decomposition defect). May split backend/frontend. |
| STORY-006 | ~400 | **Split expected** backend/frontend if friction |
| STORY-007 | ~400 | **Split expected** backend/frontend if friction |
| STORY-008 | ~300 | Migration + aggregator + controller |
| STORY-009 | ~200 | Seed data + README/docs + release PR |
| STORY-010 | ~350 | WireMock-backed tests keep CI hermetic; live call opt-in via env var |

---

## Decomposition Quality Report

- [x] **Completeness**: All v2 requirements from `proto_v2_en.md` covered (Auth, Market Data v2, Portfolio, Alerts, TimescaleDB, Modulith boundaries)
- [x] **No orphan requirements**: All four "PRIORITY 1" items and both "PRIORITY 2" items mapped to stories
- [x] **Self-containment**: Each story file includes goal, steps, Flyway migrations, tests, acceptance criteria, out-of-scope, related files
- [x] **Dependency correctness**: DAG verified — no cycles, no story depends on a later-numbered story
- [x] **Consistent IDs**: STORY-001 through STORY-009
- [x] **Testable criteria**: Each story has concrete acceptance criteria
- [x] **Out-of-scope explicit**: Every story lists what it does NOT cover
- [x] **PR-size aware**: Stories flagged for likely split during implementation
- [x] **Gitflow compliant**: Every feature branch named per `standards/git-workflow.md`, PRs target `develop`
- [x] **User decisions reflected**: JDBC over JPA, no Redis, no multi-tenancy, no ArchUnit — all applied consistently
