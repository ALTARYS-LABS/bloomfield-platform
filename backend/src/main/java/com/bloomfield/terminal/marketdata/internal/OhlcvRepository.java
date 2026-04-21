package com.bloomfield.terminal.marketdata.internal;

import com.bloomfield.terminal.marketdata.api.CandleInterval;
import com.bloomfield.terminal.marketdata.api.OhlcvCandle;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

/**
 * Accès aux bougies OHLCV stockées dans l'hypertable TimescaleDB.
 *
 * <p>On s'appuie ici sur {@link NamedParameterJdbcTemplate} plutôt que sur un {@code
 * CrudRepository} Spring Data JDBC pour deux raisons spécifiques à TimescaleDB : (1) la clé
 * primaire composite {@code (ticker, bucket)} est pénible à exprimer avec Spring Data JDBC, et (2)
 * les requêtes agrégées exploitent des fonctions propres à TimescaleDB ({@code time_bucket},
 * agrégat continu {@code ohlcv_hourly}) qui ne se marient pas naturellement avec les dérivations de
 * méthodes. Le dépôt reste confiné au package {@code internal/} conformément à la règle #1.
 */
@Repository
public class OhlcvRepository {

  private final NamedParameterJdbcTemplate jdbc;

  public OhlcvRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** Insère ou met à jour une bougie pour le couple (ticker, bucket). */
  public void upsert(
      String ticker,
      Instant bucket,
      BigDecimal open,
      BigDecimal high,
      BigDecimal low,
      BigDecimal close,
      long volume) {
    var params =
        new MapSqlParameterSource()
            .addValue("ticker", ticker)
            .addValue("bucket", java.sql.Timestamp.from(bucket))
            .addValue("open", open)
            .addValue("high", high)
            .addValue("low", low)
            .addValue("close", close)
            .addValue("volume", volume);
    jdbc.update(
        """
        INSERT INTO ohlcv (ticker, bucket, open, high, low, close, volume)
        VALUES (:ticker, :bucket, :open, :high, :low, :close, :volume)
        ON CONFLICT (ticker, bucket) DO UPDATE SET
          open = EXCLUDED.open,
          high = EXCLUDED.high,
          low = EXCLUDED.low,
          close = EXCLUDED.close,
          volume = EXCLUDED.volume
        """,
        params);
  }

  /** Retourne le bucket le plus récent persisté pour un ticker (utilisé pour le warm-up). */
  public Optional<Instant> latestBucket(String ticker) {
    var rows =
        jdbc.queryForList(
            "SELECT MAX(bucket) AS m FROM ohlcv WHERE ticker = :ticker", Map.of("ticker", ticker));
    if (rows.isEmpty() || rows.get(0).get("m") == null) {
      return Optional.empty();
    }
    return Optional.of(((java.sql.Timestamp) rows.get(0).get("m")).toInstant());
  }

  /**
   * Récupère les bougies dans l'intervalle demandé, résolues selon la granularité {@code interval}.
   *
   * <p>Sémantique verrouillée pour le contrôleur : on renvoie les {@code limit} bougies les plus
   * récentes dans la fenêtre {@code [from, to]}, triées par ordre chronologique ascendant (format
   * attendu par lightweight-charts côté frontend).
   */
  public List<OhlcvCandle> findRange(
      String ticker, CandleInterval interval, Instant from, Instant to, int limit) {
    var params =
        new MapSqlParameterSource()
            .addValue("ticker", ticker)
            .addValue("from", java.sql.Timestamp.from(from))
            .addValue("to", java.sql.Timestamp.from(to))
            .addValue("limit", limit);

    // On sélectionne d'abord les N bougies les plus récentes (DESC LIMIT) puis on inverse en ASC
    // pour obtenir l'ordre chronologique attendu par le graphique.
    String sql =
        switch (interval) {
          case ONE_MINUTE ->
              """
          SELECT * FROM (
            SELECT bucket, open, high, low, close, volume
            FROM ohlcv
            WHERE ticker = :ticker AND bucket BETWEEN :from AND :to
            ORDER BY bucket DESC
            LIMIT :limit
          ) t ORDER BY bucket ASC
          """;
          case FIVE_MINUTES ->
              """
          SELECT * FROM (
            SELECT
              time_bucket('5 minutes', bucket) AS bucket,
              first(open, bucket)  AS open,
              max(high)            AS high,
              min(low)             AS low,
              last(close, bucket)  AS close,
              sum(volume)          AS volume
            FROM ohlcv
            WHERE ticker = :ticker AND bucket BETWEEN :from AND :to
            GROUP BY time_bucket('5 minutes', bucket)
            ORDER BY bucket DESC
            LIMIT :limit
          ) t ORDER BY bucket ASC
          """;
          case ONE_HOUR ->
              """
          SELECT * FROM (
            SELECT bucket, open, high, low, close, volume
            FROM ohlcv_hourly
            WHERE ticker = :ticker AND bucket BETWEEN :from AND :to
            ORDER BY bucket DESC
            LIMIT :limit
          ) t ORDER BY bucket ASC
          """;
          case ONE_DAY ->
              """
          SELECT * FROM (
            SELECT
              time_bucket('1 day', bucket) AS bucket,
              first(open, bucket)  AS open,
              max(high)            AS high,
              min(low)             AS low,
              last(close, bucket)  AS close,
              sum(volume)          AS volume
            FROM ohlcv
            WHERE ticker = :ticker AND bucket BETWEEN :from AND :to
            GROUP BY time_bucket('1 day', bucket)
            ORDER BY bucket DESC
            LIMIT :limit
          ) t ORDER BY bucket ASC
          """;
        };

    return jdbc.query(
        sql,
        params,
        (rs, rowNum) ->
            new OhlcvCandle(
                rs.getTimestamp("bucket").toInstant().getEpochSecond(),
                rs.getBigDecimal("open"),
                rs.getBigDecimal("high"),
                rs.getBigDecimal("low"),
                rs.getBigDecimal("close"),
                rs.getLong("volume")));
  }

  /**
   * Upsert batché : une seule aller-retour SQL pour toutes les bougies d'un lot (utilisé par {@code
   * HistoricalCandleLoader} après chaque chunk Sikafinance).
   */
  public void upsertBatch(List<HistoricalBar> bars) {
    if (bars.isEmpty()) {
      return;
    }
    SqlParameterSource[] batch = new SqlParameterSource[bars.size()];
    for (int i = 0; i < bars.size(); i++) {
      HistoricalBar bar = bars.get(i);
      batch[i] =
          new MapSqlParameterSource()
              .addValue("ticker", bar.ticker())
              .addValue("bucket", java.sql.Timestamp.from(bar.bucket()))
              .addValue("open", bar.open())
              .addValue("high", bar.high())
              .addValue("low", bar.low())
              .addValue("close", bar.close())
              .addValue("volume", bar.volume());
    }
    jdbc.batchUpdate(
        """
        INSERT INTO ohlcv (ticker, bucket, open, high, low, close, volume)
        VALUES (:ticker, :bucket, :open, :high, :low, :close, :volume)
        ON CONFLICT (ticker, bucket) DO UPDATE SET
          open = EXCLUDED.open,
          high = EXCLUDED.high,
          low = EXCLUDED.low,
          close = EXCLUDED.close,
          volume = EXCLUDED.volume
        """,
        batch);
  }

  /**
   * Retourne l'ensemble des jours calendaires (UTC) pour lesquels au moins une bougie existe dans
   * la fenêtre demandée. Utilisé par {@code HistoricalCandleLoader} pour ne pas ré-interroger
   * Sikafinance sur des jours déjà persistés.
   */
  public Set<LocalDate> distinctDays(String ticker, Instant from, Instant to) {
    var params =
        new MapSqlParameterSource()
            .addValue("ticker", ticker)
            .addValue("from", java.sql.Timestamp.from(from))
            .addValue("to", java.sql.Timestamp.from(to));
    var rows =
        jdbc.queryForList(
            """
            SELECT DISTINCT (bucket AT TIME ZONE 'UTC')::date AS d
            FROM ohlcv
            WHERE ticker = :ticker AND bucket BETWEEN :from AND :to
            """,
            params);
    Set<LocalDate> days = new HashSet<>();
    for (var row : rows) {
      // PostgreSQL renvoie java.sql.Date ; on passe par toLocalDate() pour éviter le fuseau JVM.
      days.add(((java.sql.Date) row.get("d")).toLocalDate());
    }
    return days;
  }

  /**
   * Force le rafraîchissement d'un intervalle de l'agrégat continu horaire (utilitaire test).
   *
   * <p>TimescaleDB n'accepte pas les paramètres liés dans {@code CALL refresh_continuous_aggregate}
   * sur certaines versions — on injecte donc les bornes ISO-8601 de {@link Instant#toString()} que
   * Postgres accepte comme littéraux timestamptz. Les entrées proviennent du code de test ; pas
   * d'exposition utilisateur.
   */
  public void refreshHourlyAggregate(Instant from, Instant to) {
    jdbc.getJdbcTemplate()
        .execute("CALL refresh_continuous_aggregate('ohlcv_hourly', '" + from + "', '" + to + "')");
  }
}
