-- Création des tables pour le module d'alertes

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
