# STORY-011 — Chart Window Label Honesty + Simulated Mode History Seeding

**Type**: feat
**Status**: todo
**Branch**: `feat/chart-window-label-and-simulated-history`
**Depends on**: STORY-008, STORY-010
**Estimated PR size**: ~250 lines (small backend seeder + small frontend label fix + tests)

---

## Context

Observed on staging (2026-04-19, 18:37) with `app.marketdata.history-source=simulated` (default):

- Chart header reads **"GRAPHIQUE — UNLC  30J"**.
- All candles rendered are stamped **19 Apr '26** (the server's current day).
- The x-axis labels collapse to a repeated "19" because every bucket falls on the same calendar day.
- Each candle has an unrealistic ~12% intraday range because the live simulator's random walk was never tuned for OHLC aggregation over minutes.

Two distinct user-visible defects combine here:

1. **Label honesty**: the chart claims "30J" while the data covers a few hours of the current day. In simulated mode there is no history older than the last `bootRun`, so the label is structurally misleading.
2. **Empty-state ugliness**: a freshly booted simulated backend has no historical candles. The chart looks broken until the aggregator has run long enough to produce visible bars. For a jury demo that's a bad first impression.

STORY-010 (Sikafinance adapter) fixes both issues *when the flag is flipped* — but the default remains `simulated` for CI hermeticity, and the demo profile should not require network access to render a plausible chart.

---

## Goal

Make the chart honest about what it is showing in simulated mode, and give simulated mode enough synthetic history that the default demo state looks credible.

Two independent tracks, same PR (small enough):

### Track A — Frontend label honesty

When the returned candle series spans a single calendar day, relabel the window from **"30J" / "7J" / etc.** to **"Intraday"** (or the user's choice of wording). Drive this purely from the response payload — no new API contract.

Rules:
- Range detection: compute `(maxTime - minTime)` over the returned candles. If it is less than 24 h, show "Intraday". Otherwise show the current user-selected preset ("30J", "7J", "1J" on short windows, etc.).
- Keep the interval selector untouched; only the *window label* changes.
- No server round-trip for the decision — it is derived from the payload already in hand.

### Track B — Backend synthetic daily seeder (simulated mode only)

Under `@Profile("demo")` OR when `app.marketdata.history-source=simulated`, pre-seed N synthetic **daily** bars on startup so a fresh `bootRun` already has a plausible 30-day history to display.

Design:
- New `internal/SimulatedHistorySeeder` bean in `marketdata/internal/`.
- `@ConditionalOnProperty(name = "app.marketdata.simulated.seed-history-days", havingValue = "true", matchIfMissing = false)` — opt-in via config so CI tests stay deterministic unless they flip the flag.
- On `ApplicationReadyEvent`, for each ticker in the seeded 45, generate daily OHLCV bars for the last `N` trading days (weekends skipped, same calendar as STORY-010's `MissingRangeResolver`).
- Use the existing random-walk generator that powers `SimulatedMarketDataProvider`, but reset the seed per ticker with a stable hash so restarts produce the same fake history (reproducible demos).
- Idempotent: `OhlcvRepository.distinctDays(...)` check before insert, same pattern as `HistoricalCandleLoader`.
- Default `N = 30` trading days (aligned with the "30J" preset).

Bucketing: 15:30 UTC session close, identical to STORY-010's convention — so the two adapters are interchangeable at read time.

### Track C (optional follow-up, do not include unless trivial) — Simulator volatility tuning

The simulator currently emits ~±12% per-candle ranges. This is a pre-existing defect unrelated to STORY-010 but visible in the same screenshot. Out of scope for this story; file separately if it bites the demo.

---

## Why this is not part of STORY-010

STORY-010 is the *real-data* adapter. It is gated OFF by default for good reasons (ToS grey zone, undocumented endpoint, no IP budget for CI runs). The simulator mode is the CI + offline demo default, and it needs its own polish. Mixing the two in one story muddies the "what happens when I flip the flag" conversation.

---

## Acceptance Criteria

- [ ] **Track A**: In simulated mode with no history, the chart header reads "Intraday" (or agreed wording) instead of "30J". Verified with a Playwright/Cypress or at minimum a Vitest component test against a mocked single-day payload.
- [ ] **Track A**: In Sikafinance mode (STORY-010 flag on) with multi-day data, the header still reads "30J" / the selected preset.
- [ ] **Track B**: With `app.marketdata.simulated.seed-history-days=true`, a fresh `./gradlew bootRun` populates 30 daily bars per ticker within 5 seconds of `ApplicationReadyEvent`. Chart renders 30 distinct daily candles immediately.
- [ ] **Track B**: Re-running `bootRun` does not duplicate rows (idempotent). Verified by integration test.
- [ ] **Track B**: Default (`seed-history-days=false` or unset) preserves current behavior — no unexpected CI churn.
- [ ] Demo profile (`SPRING_PROFILES_ACTIVE=demo`) auto-enables the seeder by setting the property in `application-demo.yml`.
- [ ] No new Flyway migration. Pure runtime insert via `OhlcvRepository.upsertBatch`.

---

## Out of Scope

- Tuning the live tick simulator's volatility (Track C above — separate story if needed).
- Backfilling historical data over WebSocket to already-connected clients. New history is only visible on the next REST fetch.
- Rewriting `SimulatedMarketDataProvider` to produce backdated bars continuously. One-shot seeder is enough for a demo.

---

## Related Files

- `backend/src/main/java/com/bloomfield/terminal/marketdata/internal/SimulatedHistorySeeder.java` (new)
- `backend/src/main/java/com/bloomfield/terminal/marketdata/internal/OhlcvRepository.java` (reuse `distinctDays`, `upsertBatch`)
- `backend/src/main/resources/application.yml` (new key `app.marketdata.simulated.seed-history-days`)
- `backend/src/main/resources/application-demo.yml` (enable the seeder by default in demo profile)
- `frontend/src/features/chart/CandleChart.tsx` (or wherever the "30J" label lives — map returned payload span to header text)
- `backend/src/test/java/com/bloomfield/terminal/marketdata/internal/SimulatedHistorySeederIT.java` (new, Testcontainers)

---

## Notes

- The header-label change must not couple to the interval parameter. The source of truth is always the returned data's time span — that way when STORY-010 is enabled and real data spans months, the label is correct for free.
- Keep the seeder random walk deterministic per ticker so screenshots in docs stay stable release-to-release.
