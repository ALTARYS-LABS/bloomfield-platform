package com.bloomfield.terminal.marketdata.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Pré-remplit l'hypertable {@code ohlcv} avec des bougies journalières synthétiques au démarrage
 * lorsqu'aucune source historique réelle n'est disponible (mode simulé).
 *
 * <p>Gated par {@code app.marketdata.simulated.seed-history-days=true}, par défaut désactivé.
 * L'intention : offrir au mode simulé un visuel de graphique crédible sans dépendre de STORY-010
 * (adaptateur Sikafinance) ni attendre que l'agrégateur live ait accumulé plusieurs jours.
 *
 * <p>Reproductibilité : la PRNG est semée sur {@code Objects.hash(ticker)} pour que les screenshots
 * de démo restent stables d'une release à l'autre. Idempotent via {@link
 * OhlcvRepository#distinctDays(String, Instant, Instant)} — un second boot ne réécrit rien.
 *
 * <p>Convention de bucketisation : 15:30 UTC à la date de clôture de séance, identique à STORY-010,
 * pour que les deux chemins (simulé pré-seedé, Sikafinance) soient lisibles par la même requête
 * {@code findRange}.
 */
@Component
@ConditionalOnProperty(name = "app.marketdata.simulated.seed-history-days", havingValue = "true")
final class SimulatedHistorySeeder {

  private static final Logger log = LoggerFactory.getLogger(SimulatedHistorySeeder.class);

  /** Heure de clôture BRVM alignée avec STORY-010 (Africa/Abidjan UTC+0, pas de DST). */
  private static final LocalTime SESSION_CLOSE = LocalTime.of(15, 30);

  /** Volume synthétique minimal pour qu'un histogram chart ait de quoi afficher. */
  private static final long BASE_VOLUME = 1_000L;

  private final TickerSeedLoader seedLoader;
  private final OhlcvRepository repository;
  private final SimulatedHistoryProperties props;

  SimulatedHistorySeeder(
      TickerSeedLoader seedLoader, OhlcvRepository repository, SimulatedHistoryProperties props) {
    this.seedLoader = seedLoader;
    this.repository = repository;
    this.props = props;
  }

  /**
   * Point d'entrée : déclenché une fois l'application prête. On évite un {@code ApplicationRunner}
   * pour ne pas retarder la disponibilité HTTP — le seeder tourne en arrière-plan après l'événement
   * ready (le graphique affiche simplement « Intraday » en attendant si l'utilisateur se connecte
   * dans la même seconde).
   */
  @EventListener(ApplicationReadyEvent.class)
  void seedOnStartup() {
    List<TickerSeed> seeds = seedLoader.seeds();
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    LocalDate windowStart = today.minusDays(props.seedDays() * 2L);
    Instant windowStartInstant = windowStart.atStartOfDay().toInstant(ZoneOffset.UTC);
    Instant windowEndInstant = today.atTime(SESSION_CLOSE).toInstant(ZoneOffset.UTC);

    int totalSeeded = 0;
    for (TickerSeed seed : seeds) {
      Set<LocalDate> existing =
          repository.distinctDays(seed.ticker(), windowStartInstant, windowEndInstant);
      List<HistoricalBar> bars =
          buildBars(seed, tradingDaysEndingAt(today, props.seedDays()), existing);
      if (!bars.isEmpty()) {
        repository.upsertBatch(bars);
        totalSeeded += bars.size();
      }
    }
    log.info(
        "Simulated history seeder wrote {} bars across {} tickers (window: last {} trading days)",
        totalSeeded,
        seeds.size(),
        props.seedDays());
  }

  /**
   * Calcule la liste des jours ouvrés (weekends exclus) se terminant à {@code endDay} inclus, du
   * plus ancien au plus récent.
   */
  static List<LocalDate> tradingDaysEndingAt(LocalDate endDay, int count) {
    List<LocalDate> days = new ArrayList<>(count);
    LocalDate cursor = endDay;
    while (days.size() < count) {
      if (!MissingRangeResolver.isWeekend(cursor)) {
        days.add(cursor);
      }
      cursor = cursor.minusDays(1);
    }
    days.sort(LocalDate::compareTo);
    return days;
  }

  /**
   * Génère une marche aléatoire journalière pour un ticker, en sautant les jours déjà présents en
   * base. La même graine produit toujours la même séquence => démos reproductibles.
   */
  private List<HistoricalBar> buildBars(
      TickerSeed seed, List<LocalDate> tradingDays, Set<LocalDate> existing) {
    Random rng = new Random(Objects.hash(seed.ticker()));
    BigDecimal price = seed.openPrice().setScale(2, RoundingMode.HALF_UP);
    List<HistoricalBar> bars = new ArrayList<>();
    for (LocalDate day : tradingDays) {
      // On avance la marche aléatoire même sur un jour déjà en base : la graine reste cohérente
      // pour la suite, ce qui évite qu'un second boot produise une trajectoire différente après
      // que le premier a écrit une partie des jours.
      BigDecimal open = price;
      double drift = (rng.nextDouble() * 2.0 - 1.0) * props.dailyVolatility();
      BigDecimal close =
          price.multiply(BigDecimal.valueOf(1.0 + drift)).setScale(2, RoundingMode.HALF_UP);
      double highDelta = rng.nextDouble() * props.dailyVolatility();
      double lowDelta = rng.nextDouble() * props.dailyVolatility();
      BigDecimal high =
          open.max(close)
              .multiply(BigDecimal.valueOf(1.0 + highDelta))
              .setScale(2, RoundingMode.HALF_UP);
      BigDecimal low =
          open.min(close)
              .multiply(BigDecimal.valueOf(1.0 - lowDelta))
              .setScale(2, RoundingMode.HALF_UP);
      long volume = BASE_VOLUME + rng.nextLong(BASE_VOLUME * 4);
      price = close;

      if (existing.contains(day)) {
        continue;
      }
      Instant bucket = day.atTime(SESSION_CLOSE).toInstant(ZoneOffset.UTC);
      bars.add(new HistoricalBar(seed.ticker(), bucket, open, high, low, close, volume));
    }
    return bars;
  }
}
