# STORY-006 — Portfolio Module (Positions, Real-Time Valuation, P&L)

**Type**: feat
**Status**: todo
**Branch**: `feat/portfolio-module`
**Depends on**: STORY-003, STORY-004
**Estimated PR size**: ~400 lines (split backend/frontend if needed)

---

## Context

Priority-1 demo feature: each authenticated user has a portfolio of BRVM positions with live valuation and unrealized P&L computed against the current simulated market price. Realized P&L is computed from a trade history table.

All financial math uses BigDecimal. Positions and trades are simulated data seeded per demo user.

---

## What Needs to Be Done

### Step 1 — New module `portfolio`
```
com.bloomfield.terminal.portfolio
├── api/              # PortfolioController + DTOs (PortfolioSummary, PositionView, PnL)
│   └── dto/          # request/response records
├── domain/           # Portfolio, Position, Trade
├── internal/         # repositories + PnlCalculator
└── package-info.java # @ApplicationModule (allowed deps: user api, marketdata api)
```
Package convention matches the `user` module (`api/` holds the controller and DTOs; `web/` is not used). Per refactor `7d4692a`.

### Step 2 — Flyway migration `V003__portfolio_module.sql`
```sql
CREATE TABLE portfolios (
  id         UUID PRIMARY KEY,
  user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  name       VARCHAR(255) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE positions (
  id            UUID PRIMARY KEY,
  portfolio_id  UUID NOT NULL REFERENCES portfolios(id) ON DELETE CASCADE,
  ticker        VARCHAR(16) NOT NULL,
  quantity      NUMERIC(20, 6) NOT NULL,
  avg_cost      NUMERIC(20, 4) NOT NULL,
  UNIQUE (portfolio_id, ticker)
);

CREATE TABLE trades (
  id            UUID PRIMARY KEY,
  portfolio_id  UUID NOT NULL REFERENCES portfolios(id) ON DELETE CASCADE,
  ticker        VARCHAR(16) NOT NULL,
  side          VARCHAR(4) NOT NULL CHECK (side IN ('BUY', 'SELL')),
  quantity      NUMERIC(20, 6) NOT NULL,
  price         NUMERIC(20, 4) NOT NULL,
  executed_at   TIMESTAMPTZ NOT NULL,
  realized_pnl  NUMERIC(20, 4)
);

CREATE INDEX idx_positions_portfolio ON positions(portfolio_id);
CREATE INDEX idx_trades_portfolio_executed ON trades(portfolio_id, executed_at DESC);
```

### Step 3 — P&L calculation
- **Unrealized P&L per position** = `quantity × (currentPrice - avgCost)` where `currentPrice` comes from `MarketDataProvider.tickerState(ticker)`
- **Realized P&L** = sum of `trades.realized_pnl` (computed FIFO at trade execution)
- **Portfolio total value** = sum of `quantity × currentPrice`
- **Portfolio daily change %** = weighted by position value

All computations in `PnlCalculator`, pure function, easily unit-testable.

### Step 4 — REST endpoints
- `GET /api/portfolio` — current user's portfolio summary (positions + totals + P&L)
- `GET /api/portfolio/trades?limit=50` — recent trades
- `POST /api/portfolio/trades` — submit a simulated BUY/SELL (optional for v2 demo, stub OK)

All endpoints require auth; VIEWER and above allowed.

### Step 5 — WebSocket push
Broadcast portfolio updates to `/user/queue/portfolio` every 2s (or on quote tick, whichever is simpler). STOMP user destinations rely on the authenticated WS session established by the `ChannelInterceptor` added in STORY-005 Step 0 — no additional WS auth work is needed here.

### Step 6 — Seed data
**Moved to STORY-009.** Demo-user and demo-portfolio seeding is consolidated there under a single `@Profile("demo")` bean so all demo data lives in one place and ships only when the `demo` profile is active. This story creates empty tables; the portfolio becomes populated when a user signs up and makes a trade (or when STORY-009's demo profile runs).

### Step 7 — Frontend
- New tab in Terminal: "Portfolio"
- Component `PortfolioWidget` — table of positions with live P&L coloring (green/red), portfolio totals header
- Subscribe to `/user/queue/portfolio` via existing STOMP client

### Step 8 — Tests
- `PnlCalculatorTest` — various scenarios (long only, partial sells, zero quantity, price gaps)
- `PortfolioRepositoryTest` — TestContainers, position upsert idempotency
- `PortfolioControllerIT` — auth required, returns only the caller's portfolio
- `ApplicationModulesTest` — verify no direct access from `portfolio` → `marketdata.internal`

---

## Acceptance Criteria

- [ ] Authenticated user sees their portfolio at `/terminal` Portfolio tab
- [ ] Position prices update live; P&L recomputes on every tick
- [ ] Another user's portfolio is never exposed
- [ ] Unrealized + realized P&L math verified by unit tests
- [ ] All amounts are BigDecimal end to end (DB `NUMERIC`, Java `BigDecimal`, JSON serialized as string to avoid JS float loss — configure Jackson)
- [ ] Flyway migrations apply clean
- [ ] `./gradlew test` green, `pnpm build` green

---

## Out of Scope

- Real trading execution
- Order management (alerts cover notifications; trading is not in v2)
- Multi-currency (XOF only for v2)
- Benchmarks / attribution

---

## Related Files

- `backend/src/main/java/com/bloomfield/terminal/portfolio/**` (new)
- `backend/src/main/resources/db/migration/V003__portfolio_module.sql` (new)
- `backend/src/test/java/com/bloomfield/terminal/portfolio/**` (new)
- `frontend/src/components/PortfolioWidget.tsx` (new)
- `frontend/src/pages/Terminal.tsx` (add tab)
