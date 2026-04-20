## Bloomfield Terminal - Advanced model v2

### Business context
We responded to the Bloomfield Intelligence RFP (AO_BI_2026_001) for the development of
bloomfield Terminal - a professional financial information platform for African
african markets (BRVM). A first mock-up, whose source code resides in the current directory, has already been delivered to the client.
It concerns the multi-screen responsive display (adapted to mobile screens) of BRVM prices in real time (Java/Spring Boot/STOMP + React 19).

The aim of this new development is to make v2 of the previous one.
1. Defense if shortlisted - impressive live demo
2. Technical base reusable in production if awarded contract
3. Knowledge base for ALTARYS development team

---

### Stack imposed - same as current stack
- **Backend**: Java 25 / Spring Boot 4 / Spring Modulith / Spring Data JDBC (not JPA)
- **Frontend** : React 19 / TypeScript / ShadCN UI / Tailwind CSS
- **Realtime** : WebSocket / STOMP (already functional on v1)
- **DBD**: PostgreSQL + TimescaleDB (time series) + Redis (cache + pub/sub)
- **Auth**: JWT / Spring Security
- **DevOps** : Docker Compose (local dev) / GitHub Actions

---

### Non-negotiable architectural constraints
- **Spring Modulith**: boundaries defined from the outset, zero direct calls between modules
- **Spring Data JPA
- **RBAC** : ADMIN / ANALYST / VIEWER roles - access control via Spring Security annotation
- **BigDecimal** for all financial amounts and rates - never double/float
- **TestContainers** for repository tests
- **Flyway** for all SQL migrations - no ddl-auto

---

### Modules to implement (by priority)

**PRIORITY 1 - Defense (3-4 weeks)**
1. Auth : JWT registration/login, ADMIN/ANALYST/VIEWER roles, refresh token
2. BRVM live courses: improvement of existing courses, multi-instrument, sector/type filters
3. Portfolio dashboard: positions, real-time valuation, P&L (simulated data OK)
4. Price alerts: configurable thresholds per instrument, WebSocket notifications

**PRIORITY 2 - Production** base
5. TimescaleDB: historical OHLCV ingestion, graphical API (1d/1s/1m/1y)
6. Spring Modulith boundaries installed and tested with ArchUnit

---

### BRVM market data
- No official BRVM API available - use a **realistic simulator**
- Simulator must produce: ticker, price, variation %, volume, timestamp
- All ~45 BRVM-listed securities must be present
- Architecture: the simulator is a **replaceable component** with a real feed in production
- Imposed pattern: `MarketDataProvider` interface with impl `SimulatedMarketDataProvider`
- Negotiation of official BRVM API access is planned for Phase 0 of the real project

---

### Expected Spring Modulith module structure

com.bloomfield.terminal
├── user/ # auth, roles, JWT, Spring Security
├── market-data/ # courses, OHLCV history, simulator
├── portfolio/ # portfolios, positions, P&L
├── alerts/ # rules, triggering, WS notifications
└── shared-kernel/ # shared ValueObjects, events, exceptions

---

### User model

Bloomfield Terminal (mono-instance)
└── Users
├── ADMIN → user management, platform configuration
├── ANALYST → full data access, watchlist/alert creation
└── VIEWER → read-only, limited access depending on subscription

---

### What I expect first

1. **Analysis and questions** if architectural choices require arbitration
2. **Project structure**: complete Maven multi-module tree + Spring Modulith
3. **Docker Compose**: PostgreSQL + TimescaleDB + Redis + app backend
4. **User module**: User/Role entities, Spring Security JWT, FilterChain, auth endpoints
5. **market-data module**: MarketDataProvider interface + SimulatedMarketDataProvider,
   broadcast WebSocket STOMP, TimescaleDB hypertable OHLCV schema
6. **Testing**: TestContainers on each repository, ArchUnit on Modulith boundaries

## Questions
1. i wonder if I really need redis at this stage
2. i don't think I need ArchUnit because with SpringModulith, the following test verifies that the modular architecture is respected:
```java
var modules = ApplicationModules.of(TerminalApplication.class);
    modules.verify(); 
````

---

### Delivery conventions
- Complete, compilable code - no pseudo-code
- Each class with its own imports
- SQL migrations via Flyway (no ddl-auto)
- Startup README: `docker compose up` then `./mvnw spring-boot:run`
  → application up and running in less than 5 minutes

## First and foremost
- In view of what's already been done, what do you honestly think of this new application?
- Do you see any inconsistencies?
- Act as my strategic advisor and as the chief software architect for my company ALTARYS LABS.
- Correct me if I'm wrong.
- show a knack of teaching.
- Throughout this session, always propose a plan that I'll validate before taking any action
