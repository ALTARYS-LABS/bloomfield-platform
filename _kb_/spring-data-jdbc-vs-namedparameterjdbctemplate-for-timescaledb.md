# Spring Data JDBC vs `NamedParameterJdbcTemplate` for TimescaleDB

## TL;DR

Spring Data JDBC works fine on TimescaleDB for ordinary CRUD — a hypertable is just a PostgreSQL table with extra machinery underneath. But for read-mostly analytical tables that rely on Timescale-specific SQL (`time_bucket`, continuous aggregates, gapfill), dropping to `NamedParameterJdbcTemplate` inside an `internal/` package is usually **the simpler path**, not a reluctant exception.

CLAUDE.md rule #1 bans raw `JdbcTemplate` **outside** `internal/` precisely to carve out this case.

---

## 1. What "suitable" actually means

TimescaleDB is PostgreSQL with extensions. The JDBC driver sees a regular table; so does Spring Data JDBC. Nothing breaks. The real question is not "does it work?" but "does Spring Data JDBC buy me anything here?"

Spring Data JDBC's value proposition is:

- **Derived queries** — `findByTickerAndBucketBetween(...)` generates the SQL for you.
- **Aggregate roots** — lifecycle of a root entity and its children, cascaded saves, `@MappedCollection`.
- **Lifecycle events** — `BeforeSaveCallback`, `AfterConvertCallback`, etc.
- **`Persistable<ID>`** for client-generated IDs (CLAUDE.md rule #13).

If the table you're talking to gives you none of these benefits, the framework is overhead.

---

## 2. What works transparently on a hypertable

These need no special handling — a Spring Data JDBC repository works exactly as on any Postgres table:

- `INSERT` / `UPDATE` / `DELETE` / `SELECT` by primary key.
- Upserts via a custom `@Query` using `ON CONFLICT`.
- Reading from a **continuous aggregate view** — it's just a view; query it like any read-model table.
- Reading range data via derived queries on an indexed timestamp column.

If your module only does the above, **keep Spring Data JDBC**. No need to drop down.

---

## 3. Where Spring Data JDBC gets awkward

These are the signals that push you toward `NamedParameterJdbcTemplate`:

### a. Timescale SQL functions in SELECTs

Derived queries can't express:

```sql
SELECT time_bucket('5 minutes', bucket) AS bucket,
       first(open, bucket) AS open,
       max(high)          AS high,
       min(low)           AS low,
       last(close, bucket) AS close,
       sum(volume)        AS volume
FROM ohlcv
WHERE ticker = :ticker AND bucket BETWEEN :from AND :to
GROUP BY 1
ORDER BY 1;
```

You'd write that as an `@Query` string. At that point you've left the "derived queries" benefit behind — you're writing raw SQL in an annotation instead of raw SQL in a repository method. Net benefit of the framework here: near zero.

Same applies to `time_bucket_gapfill(...)`, `locf(...)`, `interpolate(...)`, `first(value, ts)`, `last(value, ts)`.

### b. Composite natural keys

OHLCV's natural PK is `(ticker, bucket)`. Spring Data JDBC supports this via an embedded record id plus `Persistable<CompositeKey>`:

```java
record OhlcvId(String ticker, Instant bucket) {}

record Ohlcv(
    @Id OhlcvId id,
    BigDecimal open, BigDecimal high,
    BigDecimal low,  BigDecimal close,
    long volume,
    @Transient boolean isNew
) implements Persistable<OhlcvId> {
    @Override public OhlcvId getId() { return id; }
    @Override public boolean isNew() { return isNew; }
}
```

It works, but it's ugly enough that a 6-line upsert via `NamedParameterJdbcTemplate` is shorter and clearer.

### c. Querying two sources behind one API

STORY-008's `CandleController` routes the interval param to two physical sources:

| Interval | Source                                 |
|----------|----------------------------------------|
| `1m`     | `ohlcv` hypertable                     |
| `5m`     | `time_bucket('5 minutes', ...) ohlcv`  |
| `1h`     | `ohlcv_hourly` continuous aggregate    |
| `1d`     | `time_bucket('1 day', ...) ohlcv`      |

Modeling this as "one repository per physical table" bloats the module; modeling it as "one Spring Data JDBC repository with three `@Query` variants" still leaves the dispatch logic in the service layer. A single `OhlcvRepository` that accepts an `Interval` enum and writes the right SQL is the most direct expression of the actual requirement.

### d. Batch writes at ingestion rate

`CandleAggregator` flushes minute-boundary buckets. `Spring Data JDBC.saveAll(...)` does per-row round-trips by default. For ~40 BRVM tickers/minute this is irrelevant. If throughput ever grows, `NamedParameterJdbcTemplate.batchUpdate(...)` is the tool — and it's already where you are.

### e. DDL and Timescale admin calls

`create_hypertable(...)`, `add_continuous_aggregate_policy(...)`, `add_retention_policy(...)`, `CALL refresh_continuous_aggregate(...)` belong in **Flyway migrations**, never in a repository. Spring Data JDBC is neither helpful nor harmful here — it's simply not the layer.

Note: continuous-aggregate creation and policy calls may refuse to run inside a transaction on some Timescale versions. If Flyway errors, add at the top of the migration:

```sql
-- flyway:executeInTransaction=false
```

Or split the aggregate + policy into a separate migration.

---

## 4. Numeric mapping — non-issue

`NUMERIC(20,4)` maps to `BigDecimal` in both Spring Data JDBC and `NamedParameterJdbcTemplate`. No gotcha. CLAUDE.md rule #2 (`BigDecimal` for money) applies identically.

---

## 5. The package-placement rule

CLAUDE.md rule #1:

> Data access goes through Spring Data JDBC repositories — never raw `JdbcTemplate` outside `internal/` packages. No JPA.

The `internal/` carve-out is exactly for cases like Timescale. The rule is **not** "never use JdbcTemplate" — it's "never expose it as the public contract of a module."

So the conventional shape for STORY-008 is:

```
alerts/         <- public API of the module
alerts/internal/
    OhlcvRepository.java               // NamedParameterJdbcTemplate inside
    CandleAggregator.java
```

The controller and service talk to `OhlcvRepository` as an interface; callers outside the module never see `NamedParameterJdbcTemplate`.

---

## 6. Decision checklist

Use **Spring Data JDBC** when:

- [ ] You're modeling an aggregate root with a lifecycle (user, portfolio, alert rule).
- [ ] Queries are expressible as derived methods or short `@Query` strings over a single table.
- [ ] You need `Persistable<UUID>` for client-generated IDs (rule #13).
- [ ] Lifecycle callbacks or domain events on save matter.

Drop to **`NamedParameterJdbcTemplate` in `internal/`** when:

- [ ] You need Timescale-specific SQL (`time_bucket`, `time_bucket_gapfill`, `first`/`last` aggregates).
- [ ] The table has a composite natural key and no aggregate-root semantics.
- [ ] One logical read maps to several physical sources (hypertable + continuous aggregates).
- [ ] You need true JDBC batch writes.
- [ ] The module is analytical (read-mostly, append-only), not transactional.

---

## 7. Anti-patterns

**Don't:** put `@Query("SELECT time_bucket(...) FROM ohlcv ...")` on a Spring Data JDBC repository method and call it "following the rule." You've paid for none of the framework's benefits and inherited all of its ceremony. It's strictly worse than a dedicated `internal/OhlcvRepository` using `NamedParameterJdbcTemplate`.

**Don't:** expose `NamedParameterJdbcTemplate` from an `api/` package. That violates rule #1. Keep it `internal/`.

**Don't:** reach for `JdbcTemplate` (positional `?` params). Always prefer `NamedParameterJdbcTemplate` — named parameters, safer refactors, same performance.

**Don't:** mix the two on the same table. One writer, one reader pattern, one repository. If you find yourself with both a Spring Data JDBC repo and a `NamedParameterJdbcTemplate` repo on `ohlcv`, collapse them.

---

## 8. Worked example skeleton (STORY-008)

```java
// alerts/internal/OhlcvRepository.java
@Repository
class OhlcvRepository {
    private final NamedParameterJdbcTemplate jdbc;

    OhlcvRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    void upsert(OhlcvBucket b) {
        var sql = """
            INSERT INTO ohlcv (ticker, bucket, open, high, low, close, volume)
            VALUES (:ticker, :bucket, :open, :high, :low, :close, :volume)
            ON CONFLICT (ticker, bucket) DO UPDATE SET
              high   = GREATEST(ohlcv.high, EXCLUDED.high),
              low    = LEAST(ohlcv.low,  EXCLUDED.low),
              close  = EXCLUDED.close,
              volume = ohlcv.volume + EXCLUDED.volume
            """;
        jdbc.update(sql, toParams(b));
    }

    List<OhlcvCandle> findRange(String ticker, Interval i, Instant from, Instant to, int limit) {
        return switch (i) {
            case ONE_MIN  -> findRawRange(ticker, from, to, limit);
            case ONE_HOUR -> findHourlyRange(ticker, from, to, limit);
            case FIVE_MIN, ONE_DAY -> findBucketedRange(ticker, i.pgInterval(), from, to, limit);
        };
    }
    // ...
}
```

Public API (`/api/brvm/candles/...`) never leaks that `NamedParameterJdbcTemplate` is underneath.

---

## 9. Further reading

- Spring Data JDBC reference: https://docs.spring.io/spring-data/relational/reference/
- TimescaleDB hyperfunctions: https://docs.timescale.com/api/latest/hyperfunctions/
- `_kb_/spring-modulith-transactional-event-listeners.md` — adjacent rule about when a bean must stay a class (CGLIB proxying).
- CLAUDE.md rules #1 (data access), #2 (BigDecimal), #13 (Persistable).
