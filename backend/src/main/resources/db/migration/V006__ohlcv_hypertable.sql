-- flyway:executeInTransaction=false
-- Motif: TimescaleDB refuse add_continuous_aggregate_policy() dans un bloc transactionnel.
-- On désactive donc l'enveloppe transactionnelle de Flyway pour cette migration.

-- Active l'extension TimescaleDB (no-op si déjà présente via l'image docker).
CREATE EXTENSION IF NOT EXISTS timescaledb;

-- Table OHLCV 1 minute : clé primaire composite (ticker, bucket).
CREATE TABLE ohlcv (
  ticker VARCHAR(16) NOT NULL,
  bucket TIMESTAMPTZ NOT NULL,
  open   NUMERIC(20, 4) NOT NULL,
  high   NUMERIC(20, 4) NOT NULL,
  low    NUMERIC(20, 4) NOT NULL,
  close  NUMERIC(20, 4) NOT NULL,
  volume BIGINT NOT NULL,
  PRIMARY KEY (ticker, bucket)
);

-- Transforme la table en hypertable partitionnée par tranches de 7 jours sur la colonne bucket.
SELECT create_hypertable('ohlcv', 'bucket', chunk_time_interval => INTERVAL '7 days');

-- Index descendant pour servir rapidement les dernières N bougies d'un ticker (lecture API).
CREATE INDEX idx_ohlcv_ticker_bucket_desc ON ohlcv (ticker, bucket DESC);

-- Agrégat continu horaire : accélère les requêtes 1h sans rejouer les buckets 1 minute.
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

-- Politique de rafraîchissement : recalcule les 3 dernières heures (hors dernière heure en cours)
-- toutes les 30 minutes. Suffisant pour une démo RFP, pas pour la production.
SELECT add_continuous_aggregate_policy('ohlcv_hourly',
  start_offset     => INTERVAL '3 hours',
  end_offset       => INTERVAL '1 hour',
  schedule_interval=> INTERVAL '30 minutes');
