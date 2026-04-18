# STORY-008 — TimescaleDB Hypertable & OHLCV History API

**Type**: feat
**Status**: todo
**Branch**: `feat/timescaledb-ohlcv-history`
**Depends on**: STORY-003
**Estimated PR size**: ~300 lines

---

## Context

Today `SimulatedMarketDataProvider.generateHistory()` regenerates candles from a deterministic seed on every call — fine for the v1 demo but it does not reflect the live simulated feed and cannot support real ingestion later.

v2 introduces a TimescaleDB hypertable ingesting OHLCV candles from the live simulator, exposed via a chart-friendly API. The Postgres image was already swapped to TimescaleDB in STORY-002 — no new infra here, only the `CREATE EXTENSION` + hypertable + ingestion job.

---

## What Needs to Be Done

### Step 1 — Flyway migration `V006__ohlcv_hypertable.sql`
```sql
CREATE EXTENSION IF NOT EXISTS timescaledb;

CREATE TABLE ohlcv (
  ticker     VARCHAR(16) NOT NULL,
  bucket     TIMESTAMPTZ NOT NULL,
  open       NUMERIC(20, 4) NOT NULL,
  high       NUMERIC(20, 4) NOT NULL,
  low        NUMERIC(20, 4) NOT NULL,
  close      NUMERIC(20, 4) NOT NULL,
  volume     BIGINT NOT NULL,
  PRIMARY KEY (ticker, bucket)
);

SELECT create_hypertable('ohlcv', 'bucket', chunk_time_interval => INTERVAL '7 days');

CREATE INDEX idx_ohlcv_ticker_bucket_desc ON ohlcv (ticker, bucket DESC);
```

**Gotcha**: `CREATE EXTENSION` must run as a superuser. The Postgres user Flyway connects as must have that role or the extension must be pre-installed via `POSTGRES_INITDB_ARGS` / a Docker init script. Document the exact setup in the commit message.

### Step 2 — Candle aggregator
- `CandleAggregator` bean in `marketdata/internal/`
- Listens to `QuoteTick` events (from STORY-007) or runs a @Scheduled job every 60s
- Maintains a rolling 1-minute bucket per ticker in memory, flushes to `ohlcv` table when bucket closes
- On startup, warm up the current open bucket from the latest DB row (if any) to avoid gaps across restarts

### Step 3 — Continuous aggregates (optional but recommended)
```sql
CREATE MATERIALIZED VIEW ohlcv_hourly
WITH (timescaledb.continuous) AS
SELECT
  ticker,
  time_bucket('1 hour', bucket) AS bucket,
  first(open, bucket)  AS open,
  max(high)            AS high,
  min(low)             AS low,
  last(close, bucket)  AS close,
  sum(volume)          AS volume
FROM ohlcv
GROUP BY ticker, time_bucket('1 hour', bucket)
WITH NO DATA;

SELECT add_continuous_aggregate_policy('ohlcv_hourly',
  start_offset => INTERVAL '3 hours',
  end_offset   => INTERVAL '1 hour',
  schedule_interval => INTERVAL '30 minutes');
```
Daily view similarly. This keeps the chart API fast at coarse resolutions.

### Step 4 — Chart API
- `GET /api/brvm/candles/{ticker}?interval=1m|5m|1h|1d&from=...&to=...`
- Interval resolution maps to `ohlcv` (1m), `ohlcv_hourly` (1h), `ohlcv_daily` (1d) — 5m uses `time_bucket` on the fly over `ohlcv`
- Default window: last 200 candles if `from`/`to` omitted
- Returns lightweight-charts compatible shape `{time, open, high, low, close, volume}`

### Step 5 — Swap the frontend chart data source
`CandlestickChart.tsx` currently calls `/api/brvm/history/{ticker}` (the simulator-generated endpoint). Redirect to `/api/brvm/candles/{ticker}?interval=1d` and confirm visual equivalence. Keep the old endpoint for one release with a deprecation log, or remove it if comfortable.

### Step 6 — Tests
- `OhlcvRepositoryTest` — TestContainers with TimescaleDB image (`timescale/timescaledb:latest-pg17`), insert + range query
- `CandleAggregatorTest` — simulated tick stream → correct bucket boundaries, correct OHLC
- `CandleControllerIT` — each interval returns correctly shaped data

---

## Acceptance Criteria

- [ ] `docker compose up -d` starts TimescaleDB, `SELECT extname FROM pg_extension` includes `timescaledb`
- [ ] After 2 minutes of running, `ohlcv` has at least one row per ticker
- [ ] `/api/brvm/candles/SNTS?interval=1m` returns the latest rolling candles
- [ ] Hourly continuous aggregate populates after its refresh policy runs (manual `CALL refresh_continuous_aggregate(...)` in the test verifies)
- [ ] Frontend chart renders unchanged (visually) after swapping endpoints
- [ ] Restart-safety: kill and restart backend during market hours, no gaps in subsequent candles beyond the down window

---

## Out of Scope

- Retention policies / compression (production concern, not demo)
- Tick-level storage (only OHLCV bars)
- Multi-exchange (BRVM only)

---

## Related Files

- `backend/src/main/resources/db/migration/V006__ohlcv_hypertable.sql` (new)
- `backend/src/main/java/com/bloomfield/terminal/marketdata/internal/CandleAggregator.java` (new)
- `backend/src/main/java/com/bloomfield/terminal/marketdata/internal/OhlcvRepository.java` (new)
- `backend/src/main/java/com/bloomfield/terminal/marketdata/web/CandleController.java` (new)
- `backend/src/test/java/com/bloomfield/terminal/marketdata/**` (new tests)
- `frontend/src/components/CandlestickChart.tsx` (endpoint swap)
