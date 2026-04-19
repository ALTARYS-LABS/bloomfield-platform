package com.bloomfield.terminal.marketdata.internal;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Peuple paresseusement l'hypertable {@code ohlcv} depuis Sikafinance : sur chaque appel, calcule
 * les jours ouvrés manquants dans la fenêtre demandée, découpe en sous-fenêtres d'au plus {@code
 * maxWindowDays} jours, appelle l'upstream (avec espacement configuré) et upsert le résultat.
 *
 * <p>Idempotent : un second appel identique ne génère aucune requête amont si toutes les bougies
 * demandées sont déjà en base. Bean uniquement créé quand {@code app.marketdata.history-source =
 * sikafinance} — en mode simulé le bean est absent et {@code CandleController} ne fait rien de plus
 * que lire l'hypertable.
 */
public final class HistoricalCandleLoader {

  private static final Logger log = LoggerFactory.getLogger(HistoricalCandleLoader.class);

  private final SikafinanceClient client;
  private final OhlcvRepository repository;
  private final SikafinanceProperties props;

  HistoricalCandleLoader(
      SikafinanceClient client, OhlcvRepository repository, SikafinanceProperties props) {
    this.client = client;
    this.repository = repository;
    this.props = props;
  }

  /**
   * S'assure que {@code [from, to]} est couvert en base pour {@code ticker}. Les appels upstream
   * sont espacés d'au moins {@code requestSpacingMs} millisecondes.
   */
  public void ensureCached(String ticker, Instant from, Instant to) {
    if (to.isBefore(from)) {
      return;
    }
    LocalDate fromDay = LocalDate.ofInstant(from, ZoneOffset.UTC);
    LocalDate toDay = LocalDate.ofInstant(to, ZoneOffset.UTC);

    Set<LocalDate> existing = repository.distinctDays(ticker, from, to);
    List<DateRange> missing = MissingRangeResolver.resolve(fromDay, toDay, existing);
    if (missing.isEmpty()) {
      log.debug("History already cached for ticker={} from={} to={}", ticker, fromDay, toDay);
      return;
    }

    boolean firstCall = true;
    for (DateRange range : missing) {
      for (DateRange chunk : MissingRangeResolver.chunk(range, props.maxWindowDays())) {
        if (!firstCall) {
          sleepForSpacing();
        }
        firstCall = false;
        List<HistoricalBar> bars = client.fetchDaily(ticker, chunk.start(), chunk.end());
        repository.upsertBatch(bars);
        log.debug(
            "Sikafinance chunk ticker={} {}..{} rows={}",
            ticker,
            chunk.start(),
            chunk.end(),
            bars.size());
      }
    }
  }

  private void sleepForSpacing() {
    try {
      Thread.sleep(props.requestSpacingMs());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while spacing Sikafinance requests", e);
    }
  }
}
