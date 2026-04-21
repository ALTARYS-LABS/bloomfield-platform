# STORY-007 — Alerts Module (Price Thresholds, WS Notifications, Offline Delivery)

**Type**: feat
**Status**: todo
**Branch**: `feat/alerts-module`
**Depends on**: STORY-003, STORY-004
**Estimated PR size**: ~400 lines (split backend/frontend if needed)

---

## Context

Priority-1 demo feature: users create price alert rules (e.g. "notify me when SNTS crosses 20000"), the backend evaluates them against the live quote stream, fires a WebSocket notification to the owner, and persists the event so it is delivered when the user reconnects.

This is also the first use of **Spring Modulith events** across module boundaries: `marketdata` publishes quote events, `alerts` consumes them via `@ApplicationModuleListener` — no direct dependency.

---

## What Needs to Be Done

### Step 1 — Modulith event in `marketdata/api/`
```java
public record QuoteTick(String ticker, BigDecimal price, Instant at) {}
```
Published by the simulator on every tick via `ApplicationEventPublisher`. This is a public event — other modules may subscribe.

### Step 2 — New module `alerts`
```
com.bloomfield.terminal.alerts
├── api/              # AlertController + DTOs (AlertRuleRequest, AlertEventView)
│   └── dto/
├── domain/           # AlertRule, AlertEvent, ThresholdOperator (ABOVE, BELOW, CROSSES_UP, CROSSES_DOWN)
├── internal/         # AlertEngine (event listener), repositories, notifier
└── package-info.java # @ApplicationModule (allowed deps: user api, marketdata api)
```
Package convention matches the `user` and `portfolio` modules (`api/` holds controller + DTOs; `web/` is not used). Per refactor `7d4692a`.

### Step 3 — Flyway migration `V004__alerts_module.sql`
```sql
CREATE TABLE alert_rules (
  id         UUID PRIMARY KEY,
  user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  ticker     VARCHAR(16) NOT NULL,
  operator   VARCHAR(16) NOT NULL CHECK (operator IN ('ABOVE','BELOW','CROSSES_UP','CROSSES_DOWN')),
  threshold  NUMERIC(20, 4) NOT NULL,
  enabled    BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE alert_events (
  id           UUID PRIMARY KEY,
  rule_id      UUID NOT NULL REFERENCES alert_rules(id) ON DELETE CASCADE,
  user_id      UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  ticker       VARCHAR(16) NOT NULL,
  triggered_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  price        NUMERIC(20, 4) NOT NULL,
  delivered_at TIMESTAMPTZ,
  read_at      TIMESTAMPTZ
);

CREATE INDEX idx_alert_rules_ticker_enabled ON alert_rules(ticker) WHERE enabled;
CREATE INDEX idx_alert_events_user_undelivered ON alert_events(user_id) WHERE delivered_at IS NULL;
```

### Step 4 — `AlertEngine`
- `@ApplicationModuleListener` on `QuoteTick`
- For each active rule on that ticker: evaluate threshold (for CROSSES_* track previous price per ticker in memory)
- On fire: insert `alert_events` row, publish internal event for notification
- Debounce: once fired, rule is flagged `enabled = false` (one-shot) — simpler for v2 demo than sliding windows

### Step 5 — Notification delivery
- Send via STOMP to `/user/queue/alerts` — authenticated sessions are established by the `ChannelInterceptor` added in STORY-005 Step 0
- If user not connected: row stays `delivered_at IS NULL`; on WS reconnect, a `ReconnectHandler` (STOMP subscription hook) pushes all undelivered events, then marks them delivered

### Step 6 — REST endpoints
- `GET /api/alerts/rules` — list caller's rules
- `POST /api/alerts/rules` — create rule
- `DELETE /api/alerts/rules/{id}` — delete (caller-owned only)
- `GET /api/alerts/events?limit=50` — recent events
- `POST /api/alerts/events/{id}/read` — mark read

### Step 7 — Frontend
- New `AlertsWidget` component with two tabs: Rules (CRUD) and Events (history)
- Toast notification on new incoming `/user/queue/alerts` event
- Unread badge count on the Alerts tab

### Step 8 — Tests
- `AlertEngineTest` — unit test with synthetic `QuoteTick` stream; verifies ABOVE, BELOW, CROSSES_UP, CROSSES_DOWN behavior
- `AlertControllerIT` — CRUD per-user isolation (another user's rule returns 404 on delete)
- `AlertDeliveryIT` — rule fires → event persisted → user reconnects → undelivered events pushed → `delivered_at` set
- `ApplicationModulesTest` — verify `alerts` does not depend on `marketdata/internal`

---

## Acceptance Criteria

- [ ] Creating a rule for a price 0.1% above current SNTS triggers within seconds
- [ ] Logged-out user's triggered rule produces an `alert_events` row; reconnecting delivers it
- [ ] Deleting another user's rule returns 404 (not 403 — don't leak existence)
- [ ] Module boundaries respected (`modules.verify()` green)
- [ ] `./gradlew test` green

---

## Out of Scope

- Email / SMS / push notifications
- Complex rule types (volume, moving averages, candlestick patterns)
- Rule sharing between users
- Rule re-arming after fire (one-shot only for v2)

---

## Related Files

- `backend/src/main/java/com/bloomfield/terminal/alerts/**` (new)
- `backend/src/main/java/com/bloomfield/terminal/marketdata/api/QuoteTick.java` (new event DTO)
- `backend/src/main/resources/db/migration/V004__alerts_module.sql` (new)
- `backend/src/test/java/com/bloomfield/terminal/alerts/**` (new)
- `frontend/src/components/AlertsWidget.tsx` (new)
