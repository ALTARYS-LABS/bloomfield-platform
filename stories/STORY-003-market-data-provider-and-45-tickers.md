# STORY-003 — `MarketDataProvider` Interface & 45 BRVM Tickers

**Type**: feat
**Status**: todo
**Branch**: `feat/market-data-provider-and-45-tickers`
**Depends on**: STORY-002
**Estimated PR size**: ~400 lines

---

## Context

The v2 spec requires the market data source to be a **replaceable component** behind an interface, so that `SimulatedMarketDataProvider` can be swapped for a real BRVM feed in production without touching callers. Today, `MarketDataSimulator` is a concrete `@Service` used directly by controllers.

The spec also requires **all ~45 BRVM-listed securities**, not the 10 hardcoded today. Hardcoding 45 tickers in Java is a maintenance liability — they belong in a seed file (SQL or YAML resource) editable without recompiling.

---

## What Needs to Be Done

### Step 1 — Define the public interface
In `marketdata/api/`:

```java
public interface MarketDataProvider {
  List<Quote> currentQuotes();
  Optional<TickerState> tickerState(String ticker);
  List<OhlcvCandle> history(String ticker, int days);
  List<MarketIndex> indices();
  List<OrderBookEntry> orderBook();
}
```

Include DTO records in `api/` (public to other modules): `Quote`, `TickerState`, `OhlcvCandle`, `MarketIndex`, `OrderBookEntry`. Internal mutable types stay in `internal/`.

### Step 2 — Rename and refactor the simulator
`MarketDataSimulator` → `SimulatedMarketDataProvider` (in `internal/`), implements `MarketDataProvider`. Controllers and WebSocket broadcaster depend on the **interface**, not the class.

### Step 3 — Seed 45 BRVM tickers
Create `backend/src/main/resources/data/brvm-tickers.yml` (or `.sql` via Flyway — pick one; see "Decision" below). Each entry: `ticker`, `name`, `sector`, `type` (equity/bond), `openPrice`, `marketCap`, `per`, `dividendYield`.

Provide the full list of ~45 BRVM securities. If exact data is not available, use realistic placeholder values per sector and mark the file with a comment `# TODO: verify against official BRVM reference` so the gap is visible.

**Decision to lock during implementation**: YAML loaded at startup by `@ConfigurationProperties`, OR Flyway `V002__seed_tickers.sql` into a `tickers` table. Recommendation: **YAML for v2** — market data itself stays in memory (persistence arrives in STORY-008), so a static resource avoids introducing a half-persistence model now.

### Step 4 — Sector/type filters on the REST API
Add query params to the existing list endpoint (or introduce `/api/brvm/quotes?sector=Finance&type=equity`):
- `sector` — exact match against the ticker's sector
- `type` — `equity` | `bond`
- Both optional; no filter → all tickers

### Step 5 — Tests
- `SimulatedMarketDataProviderTest` — unit test: generates quotes with coherent OHLC (`high ≥ max(open, close)`, `low ≤ min(open, close)`), variation % calculated against open price, BigDecimal only, no float/double leaks
- `MarketDataControllerTest` — `@WebMvcTest` with mocked `MarketDataProvider`, verifies filter param handling
- `TickerSeedTest` — loads the YAML, asserts 45 entries, no duplicates, sector/type values from a known enum set

---

## Acceptance Criteria

- [ ] `MarketDataProvider` interface exists in `marketdata/api/`; no controller or other module imports the concrete `SimulatedMarketDataProvider`
- [ ] 45 tickers load at startup; `GET /api/brvm/quotes` returns 45 entries
- [ ] `?sector=Finance` filters correctly; `?type=bond` filters correctly; both combine
- [ ] All financial fields are `BigDecimal`; grep for `double`/`float` in `marketdata/` returns zero matches in production code
- [ ] Unit + MVC tests green, Spotless clean
- [ ] Frontend `MarketTable` still renders correctly (verify locally before PR — no breaking API shape change for existing consumers)

---

## Out of Scope

- Persisting historical candles (STORY-008)
- Real BRVM feed adapter (post-v2, Phase 0 of production)
- Frontend sector/type filter UI — the API supports it, UI integration is a separate small PR if needed

---

## Related Files

- `backend/src/main/java/com/bloomfield/terminal/marketdata/api/MarketDataProvider.java` (new)
- `backend/src/main/java/com/bloomfield/terminal/marketdata/internal/SimulatedMarketDataProvider.java` (renamed from `MarketDataSimulator`)
- `backend/src/main/java/com/bloomfield/terminal/marketdata/web/*Controller.java`
- `backend/src/main/resources/data/brvm-tickers.yml` (new)
- `backend/src/test/java/com/bloomfield/terminal/marketdata/**` (new tests)
