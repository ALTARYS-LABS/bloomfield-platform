# STORY-010 — Sikafinance Historical Adapter (Real BRVM Data)

**Type**: feat
**Status**: todo
**Branch**: `feat/sikafinance-historical-adapter`
**Depends on**: STORY-003, STORY-008
**Estimated PR size**: ~350 lines

---

## Context

`SimulatedMarketDataProvider` (STORY-003) produces plausible but fake OHLCV. For the jury demo, showing **real historical BRVM prices** in the candlestick chart dramatically strengthens the pitch without waiting for an official BRVM API (negotiation is Phase 0 of production).

Research of the open-source R package [`Koffi-Fredysessie/BRVM`](https://github.com/Koffi-Fredysessie/BRVM) revealed that **sikafinance.com** exposes an undocumented JSON endpoint used by their own web frontend to display historical charts. This story wraps that endpoint behind a second `MarketDataProvider` implementation so history comes from real data while live ticks stay simulated (sikafinance does not stream).

---

## The Endpoint (reverse-engineered, not officially documented)

```http
POST https://www.sikafinance.com/api/general/GetHistos
Content-Type: application/json
Origin:  https://www.sikafinance.com
Referer: https://www.sikafinance.com/marches/historiques/{TICKER}
User-Agent: Mozilla/5.0 ... (browser-like required)
```

**Body:**
```json
{
  "ticker":  "SGBCI",
  "datedeb": "2026-01-01",
  "datefin": "2026-03-31",
  "xperiod": "0"
}
```

| `xperiod` | Aggregation |
|---|---|
| `0`   | daily   |
| `7`   | weekly  |
| `30`  | monthly |
| `91`  | quarterly |
| `365` | yearly  |

**Response shape:**
```json
{
  "lst": [
    {"Date": "15/01/2026", "Open": 12500, "High": 12650, "Low": 12480, "Close": 12600, "Volume": 1250},
    ...
  ]
}
```

**Constraints inferred from the R reference implementation:**
- Max window per request: **89 days** — longer ranges must be chunked
- Self-imposed spacing: ~**111 ms** between requests (~9 req/s ceiling)
- Date format in response is `DD/MM/YYYY`

---

## What Needs to Be Done

### Step 1 — New class `SikafinanceHistoricalProvider` in `marketdata/internal/`

Implements `MarketDataProvider` (from STORY-003), but only the **history-related** methods. Live-quote methods (`currentQuotes`, `orderBook`, `indices`) delegate to the simulator — Sikafinance has no streaming API. Two options, pick one during implementation:

- **Option A (recommended): Composite provider.** A `@Primary CompositeMarketDataProvider` that routes `history()` to Sikafinance and everything else to the simulator. Clean separation, no if-branches.
- **Option B:** `SikafinanceHistoricalProvider` extends the simulator and overrides only `history()`. Simpler but tighter coupling.

### Step 2 — Spring `RestClient` configuration

```java
@Bean
RestClient sikafinanceClient(SikafinanceProperties props) {
  return RestClient.builder()
      .baseUrl(props.baseUrl())
      .defaultHeader("User-Agent", props.userAgent())
      .defaultHeader("Origin", "https://www.sikafinance.com")
      .requestInterceptor(new RateLimitInterceptor(props.requestSpacingMs()))
      .build();
}
```

`SikafinanceProperties` record (`@ConfigurationProperties("app.marketdata.sikafinance")`):
- `baseUrl` — default `https://www.sikafinance.com`
- `userAgent` — default a realistic Firefox UA string (configurable so we can rotate if blocked)
- `requestSpacingMs` — default `150` (slightly more conservative than R's 111ms)
- `maxWindowDays` — default `89`
- `connectTimeout` / `readTimeout` — defaults `5s` / `15s`

### Step 3 — Chunking + retry

Algorithm for `history(ticker, from, to)`:

```
windows = chunk(from, to, maxWindowDays)
results = []
for w in windows:
    response = POST /api/general/GetHistos  with {ticker, datedeb=w.start, datefin=w.end, xperiod=0}
    sleep(requestSpacingMs)
    results.extend(parseResponse(response))
return dedupeByDate(results)
```

Retry once on IOException / 5xx with 1s backoff. **Do not retry on 4xx** (likely a bad ticker or ToS block — log and bubble up empty).

### Step 4 — Cache into TimescaleDB

Since STORY-008 introduces the `ohlcv` hypertable, the adapter can populate it:
- On first call for a (ticker, date_range), fetch from Sikafinance, upsert into `ohlcv` (primary key is `(ticker, bucket)`, so upsert via `INSERT ... ON CONFLICT DO UPDATE`)
- Subsequent calls read from the hypertable, only hitting Sikafinance for date ranges not yet cached
- Result: Sikafinance is hit at most once per (ticker, day). Great for demo stability, friendly to the upstream.

A simple helper `MissingRangeResolver` identifies which sub-intervals of `[from, to]` are missing from the hypertable (Postgres gap query). Test this in isolation.

### Step 5 — Feature flag / config toggle

`application.yml`:
```yaml
app:
  marketdata:
    history-source: simulated   # simulated | sikafinance
    sikafinance:
      base-url: https://www.sikafinance.com
      user-agent: "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:144.0) Gecko/20100101 Firefox/144.0"
      request-spacing-ms: 150
      max-window-days: 89
```

Default `simulated` in all environments so:
- CI / tests stay **hermetic** (no external HTTP)
- Demo environment overrides via env var `APP_MARKETDATA_HISTORY_SOURCE=sikafinance`

### Step 6 — Map Sikafinance tickers

Sikafinance's ticker codes generally match BRVM codes (e.g. `SGBCI`, `SNTS`, `ECOC`) but not always. Add a mapping table `sikafinance-ticker-map.yml` (ours → theirs) so the caller keeps using our canonical ticker. Blank mapping = assume same code.

### Step 7 — Observability

- Log every outbound request at DEBUG: ticker, window, status, row count, latency
- Micrometer counters: `sikafinance.requests`, `sikafinance.errors`, `sikafinance.rows_fetched`, tags by ticker
- A single WARN log the first time a request fails (to alert the operator that upstream may be blocking us)

### Step 8 — Tests

- `SikafinanceResponseParserTest` — parse canonical response sample (fixture from real call), handle empty `lst`, handle DD/MM/YYYY dates, reject malformed JSON
- `SikafinanceHistoricalProviderTest` — use **WireMock** to stub `/api/general/GetHistos`, verify chunking (request for 250-day window → 3 calls of ≤89 days each), verify spacing, verify retry
- `MissingRangeResolverTest` — unit test: given existing rows and a requested range, compute missing sub-intervals
- **No live integration test in CI** (would be flaky + ToS risk). A `@Tag("live")` integration test can be run locally by a dev: `./gradlew test --tests '*SikafinanceLiveIT' -Dlive=true`

---

## Acceptance Criteria

- [ ] With `app.marketdata.history-source=sikafinance` and a valid ticker (e.g. SGBCI), `/api/brvm/candles/SGBCI?interval=1d&from=...&to=...` returns real OHLCV rows
- [ ] Rows are persisted in `ohlcv`; a second identical call serves from the hypertable with zero outbound HTTP (verified in WireMock test: zero requests on the second call)
- [ ] Requests are spaced by at least `request-spacing-ms` and chunked at `max-window-days`
- [ ] A Sikafinance 5xx triggers exactly one retry; a 4xx does not
- [ ] `modules.verify()` green — the adapter sits in `marketdata/internal/`, no new cross-module dependencies
- [ ] Default config (`simulated`) keeps CI hermetic: `./gradlew test` runs with zero outbound HTTP
- [ ] Frontend chart displays real BRVM closes when the demo environment is switched to `sikafinance` source

---

## Out of Scope / Risks

**Out of scope:**
- Live tick streaming — Sikafinance has no WebSocket/SSE API. Simulator still drives live quotes.
- HTML scraping of sikafinance.com for fields not in the JSON response.
- Multiple upstream sources (Rich Bourse, etc.) — single-source for v2.
- Official BRVM API integration — Phase 0 of production.

**Risks to document in the PR description:**
1. **Undocumented, unofficial endpoint** — sikafinance can change or block it without notice. The adapter is designed to degrade gracefully (falls back to empty → frontend shows "no data" rather than crashing).
2. **Terms of Service** — automated access without permission is a legal grey zone. Acceptable for a demo; **must obtain written permission before production use** or migrate to an official feed.
3. **IP blocking** — spoofed Origin/Referer may be tolerated for low volume; aggressive polling will likely get blocked. Rate limiter + caching in hypertable minimizes risk.
4. **Ticker coverage gap** — not every BRVM ticker may resolve on Sikafinance. `sikafinance-ticker-map.yml` accommodates renames; missing tickers log a WARN and return empty rather than failing.

---

## Related Files

- `backend/src/main/java/com/bloomfield/terminal/marketdata/internal/SikafinanceHistoricalProvider.java` (new)
- `backend/src/main/java/com/bloomfield/terminal/marketdata/internal/CompositeMarketDataProvider.java` (new — if Option A)
- `backend/src/main/java/com/bloomfield/terminal/marketdata/internal/SikafinanceProperties.java` (new)
- `backend/src/main/java/com/bloomfield/terminal/marketdata/internal/RateLimitInterceptor.java` (new)
- `backend/src/main/java/com/bloomfield/terminal/marketdata/internal/MissingRangeResolver.java` (new)
- `backend/src/main/resources/data/sikafinance-ticker-map.yml` (new)
- `backend/src/main/resources/application.yml` (new config section)
- `backend/src/test/java/com/bloomfield/terminal/marketdata/internal/**` (new tests)
- `backend/build.gradle.kts` (add `org.springframework.boot:spring-boot-starter-web` already present — needs `wiremock-jre8-standalone` or `org.wiremock:wiremock-standalone` in `testImplementation`)

---

## References

- R reference implementation (source of the endpoint reverse-engineering): https://github.com/Koffi-Fredysessie/BRVM/blob/main/R/brvm-get-data.R
- Sikafinance historical chart pages (Referer target): https://www.sikafinance.com/marches/historiques/{TICKER}
