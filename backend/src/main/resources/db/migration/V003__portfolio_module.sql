-- Module portefeuille : chaque utilisateur a un portefeuille, contenant N positions
-- (une par ticker, unicité imposée) et un historique de trades distinct pour le réalisé.
-- Toutes les colonnes monétaires sont en NUMERIC pour préserver la précision décimale
-- (jamais DOUBLE / FLOAT), en phase avec BigDecimal côté Java.

CREATE TABLE portfolios (
  id         UUID PRIMARY KEY,
  user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  name       VARCHAR(255) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_portfolios_user ON portfolios(user_id);

CREATE TABLE positions (
  id            UUID PRIMARY KEY,
  portfolio_id  UUID NOT NULL REFERENCES portfolios(id) ON DELETE CASCADE,
  ticker        VARCHAR(16) NOT NULL,
  quantity      NUMERIC(20, 6) NOT NULL,
  avg_cost      NUMERIC(20, 4) NOT NULL,
  UNIQUE (portfolio_id, ticker)
);

CREATE INDEX idx_positions_portfolio ON positions(portfolio_id);

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

CREATE INDEX idx_trades_portfolio_executed ON trades(portfolio_id, executed_at DESC);
