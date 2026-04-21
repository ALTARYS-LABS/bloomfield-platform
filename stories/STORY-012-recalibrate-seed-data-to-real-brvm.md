# STORY-012 — Recalibrate Seed & Static Data to Real BRVM Order of Magnitude

**Type**: chore (data calibration)
**Status**: todo
**Branch**: `chore/story-012-recalibrate-seed-data`
**Depends on**: STORY-003, STORY-009, STORY-010
**Target release**: next release after v2.0.0 (not part of v2.0.0)
**Estimated PR size**: ~300 lines (dominated by YAML diff)

---

## Context

The v2.0.0 demo runs in two modes:

- **`APP_MARKETDATA_HISTORY_SOURCE=sikafinance`** — the candle chart shows real BRVM historical OHLCV (STORY-010). The top-bar ticker and the QuotesWidget keep running on the simulator, because Sikafinance has no streaming API.
- **`APP_MARKETDATA_HISTORY_SOURCE=simulated`** (default, hermetic) — everything is simulated, including the candle chart.

In both modes, the simulator starts its random walk from seed prices baked into `backend/src/main/resources/data/brvm-tickers.yml`. Those seed prices today are "realistic placeholders per sector" (file header) and **do not match the real BRVM last close for any given ticker**. Same story for the demo portfolio positions (hardcoded `avgCost`), the demo alert thresholds, and the two index base values.

Consequence visible to the jury: if anyone compares the live-quote box for BNBC on `https://staging-bf-terminal.altaryslabs.com` with `https://www.sikafinance.com/marches/cotation_BNBC.ci`, the order of magnitude is off. On `sikafinance` history mode, this also creates a jarring mismatch between the candle chart (real prices) and the live ticker above it (placeholder prices) — the last bar of the chart can sit at a very different level than the ticker.

This story calibrates the seed data once against a Sikafinance snapshot so both modes present order-of-magnitude-plausible numbers, without changing any code logic.

---

## What Needs to Be Done

This is pure data work. Zero schema changes, zero migration, no new classes. Four files move; everything else stays.

### Step 1 — Calibrate `brvm-tickers.yml` (45 tickers × 4 fields)

File: `backend/src/main/resources/data/brvm-tickers.yml`
Fields per ticker: `openPrice`, `marketCap`, `per`, `dividendYield`

Rules:

1. **`openPrice`** — last daily close from Sikafinance on the calibration date. Round to the nearest whole XOF (BRVM quotes in whole units; `NUMERIC(20,4)` in the DB still accepts it).
2. **`marketCap`** — `openPrice × outstandingShares`. Outstanding shares are not in scope to look up per ticker; instead, round `marketCap` to the nearest **10 billion XOF bucket** based on the sector average. This is documented in the updated file header as "order-of-magnitude realistic, not firm-specific accurate".
3. **`per`** — keep the current sector-average placeholder unless the existing value is obviously broken (< 1 or > 50). Not user-visible on the demo path.
4. **`dividendYield`** — keep the current sector-average placeholder unless obviously broken (< 0% or > 20%). Not user-visible on the demo path.
5. **Bonds (the `Obligations` sector, ~10 tickers)** — deliberately **out of scope**. Keep current placeholders and retain the existing TODO in the file header about coupon / nominal values. Bonds need a different data model (coupon, maturity) that is not worth opening for this calibration round.

### Step 2 — Calibrate `DemoPortfolioSeeder.java`

File: `backend/src/main/java/com/bloomfield/terminal/portfolio/internal/DemoPortfolioSeeder.java`
Fields: 6 hardcoded `avgCost` values — SNTS, BOAC, SGBC, ONTBF, PALC, SOGC.

Rule: set each `avgCost` to roughly **90–95% of the calibrated `openPrice`** for that ticker. This produces a realistic small unrealized gain when the demo starts, rather than a contrived zero-P&L or a wildly red position. The P&L pill in the portfolio widget then shows a plausible small positive number for each line.

### Step 3 — Calibrate `DemoAlertSeeder.java`

File: `backend/src/main/java/com/bloomfield/terminal/alerts/internal/DemoAlertSeeder.java`
Fields: 3 threshold values — BOAC `ABOVE`, PALC `BELOW`, SNTS `CROSSES_UP`.

Rule: place each threshold **within ±3% of the calibrated `openPrice`** for that ticker, on the side of the comparator. This keeps alerts near-the-money so the simulated random walk has a realistic chance of firing one of them within the 8-minute demo window (STORY-009 demo-script).

### Step 4 — Calibrate index base values

File: `backend/src/main/resources/application.yml`
Fields: `market.indices.composite-base`, `market.indices.brvm10-base`.

Rule: take BRVM Composite and BRVM 10 closes from the calibration date. Two values, published on both sikafinance.com and brvm.org.

### Step 5 — Ship a reproducible calibration script (Option A)

File: `scripts/calibrate-brvm-tickers.sh` (new)

Mirrors the option-A choice made in STORY-010 (favour reproducibility over one-off paste):

- POST `/api/general/GetHistos` once per ticker for the last daily bar.
- For `marketCap`, scrape `https://www.sikafinance.com/marches/cotation_<TICKER>.ci` once per ticker (brittle HTML parse, but runs on a dev laptop once per calibration round, not in CI).
- Emit a fresh `brvm-tickers.yml` candidate to `build/brvm-tickers.calibrated.yml` for manual diff + copy into `src/main/resources/data/`.
- The calibrated YAML is reviewed by hand and committed; the raw scrape output is **not** committed.
- Script header documents the command used and the calibration date.

Alternative (Option B, rejected for this story): manual CSV paste. Faster for one round but not reproducible; worth falling back to only if the HTML scrape in Option A breaks on calibration day.

### Step 6 — Update the file header in `brvm-tickers.yml`

- Remove or shorten the generic "placeholders per sector" wording.
- Replace with a single line: `# Equity prices and caps calibrated against sikafinance.com on YYYY-MM-DD via scripts/calibrate-brvm-tickers.sh.`
- Keep the existing TODO about bonds (they remain uncalibrated this round).

### Step 7 — Verify nothing in tests asserts on the old hardcoded values

Before changing numbers, grep the backend for asserts on the literal prices we're about to move:

```bash
./gradlew test --tests '*' --info 2>/dev/null >/dev/null  # baseline green
grep -r -E "18500|5100|12300|4250|4950|4150|5215|4885|18920|234\.56|178\.23" backend/src/test
```

If any test asserts on a value we're moving, rewrite that assertion to use `roundsToReasonableMagnitude` or read from the same seed file. No test should pin to a specific YAML price.

---

## Acceptance Criteria

- [ ] With `APP_MARKETDATA_HISTORY_SOURCE=simulated`, the live-quote panel on the terminal shows values within **±10%** of sikafinance.com for the 6 seeded tickers (SNTS, BOAC, SGBC, ONTBF, PALC, SOGC) on the calibration date.
- [ ] With `APP_MARKETDATA_HISTORY_SOURCE=sikafinance`, the last candle's close and the live-ticker price for the same ticker agree to within ±10% (the jarring mismatch goes away).
- [ ] Portfolio tab shows a realistic small unrealized gain on each of the 6 seeded positions at demo start (not zero, not > 20%).
- [ ] At least one of the 3 seeded alerts fires during a 10-minute simulator run on the calibration day.
- [ ] `./gradlew test` is green with no skipped tests and no assertion rewritten to weaker semantics than before.
- [ ] `modules.verify()` still green.
- [ ] `brvm-tickers.yml` header documents the calibration date + script name.
- [ ] `scripts/calibrate-brvm-tickers.sh` is committed, runs on a clean laptop, and reproduces the committed YAML within rounding.

---

## Out of Scope / Risks

**Out of scope:**

- Dynamic re-seeding at runtime. This is a one-shot calibration against a frozen snapshot; the simulator random-walks from there.
- Live scraping of Sikafinance for the simulator. STORY-010 already does that for history; live quotes remain simulated by design.
- Bonds — kept as placeholders with the existing TODO.
- Official BRVM reference data (brvm.org API). Not available without a paid feed; deferred to Phase 0 of production (same position as STORY-010).
- Currency handling — BRVM quotes in XOF end to end.

**Risks to document in the PR description:**

1. **Calibration drift**: these values will be "wrong" within weeks as real BRVM prices move. Acceptable for demo seed data. The file header stamps the calibration date so the next developer knows when to re-run the script.
2. **Market-cap accuracy**: rounded to 10 billion XOF buckets. Order-of-magnitude plausible, not firm-specific. Call this out on the demo-script if the jury asks.
3. **Sikafinance HTML parse fragility**: the `cotation_*.ci` page layout can change. Script logs on each scrape and aborts cleanly if the expected selectors are missing — the dev then falls back to Option B manually.
4. **Terms of Service**: one-off scrape on a dev laptop is the same stance as STORY-010's adapter. Acceptable for demo; must be replaced by official data before production.

---

## Related Files

- `backend/src/main/resources/data/brvm-tickers.yml` (modify)
- `backend/src/main/java/com/bloomfield/terminal/portfolio/internal/DemoPortfolioSeeder.java` (modify)
- `backend/src/main/java/com/bloomfield/terminal/alerts/internal/DemoAlertSeeder.java` (modify)
- `backend/src/main/resources/application.yml` (modify — 2 values)
- `scripts/calibrate-brvm-tickers.sh` (new)
- `docs/demo-script.md` (minor update: mention the calibration date in the opening beat so the jury knows the numbers reflect a real snapshot)

---

## References

- STORY-003 — original ticker catalog and simulator.
- STORY-009 — seeded portfolio and alert values that this story recalibrates.
- STORY-010 — Sikafinance historical adapter and ticker-mapping precedent; Option A/B framing reused here.
- Sikafinance cotation page pattern: `https://www.sikafinance.com/marches/cotation_<TICKER>.ci`.
- BRVM official reference (for bonds once they are tackled): `https://www.brvm.org`.
