package com.bloomfield.terminal.marketdata.internal;

import com.bloomfield.terminal.marketdata.api.QuoteTick;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Agrège les {@link QuoteTick} en bougies 1 minute et persiste chaque bucket fermé dans {@code
 * ohlcv}.
 *
 * <p>Rationale « classe plutôt que record » : ce bean détient un état mutable non trivial (une map
 * de buckets en cours par ticker). Il ne porte aucune annotation AOP nécessitant un proxy CGLIB
 * (règle #5), mais l'état interne impose un type classique.
 *
 * <p>Choix de l'écouteur : {@code @EventListener} synchrone et intra-module. Pas de
 * {@code @ApplicationModuleListener} ici : l'événement est consommé dans le même module que son
 * émetteur, la sémantique transactionnelle « publish-then-listen » de Modulith n'apporte rien et
 * ajouterait une ligne d'event_publication par tick.
 *
 * <p>Warm-up au redémarrage : volontairement absent. Au premier tick suivant un redémarrage, on
 * ouvre un bucket neuf. La minute en cours pendant l'arrêt est donc perdue (borné par la fenêtre
 * d'indisponibilité), ce qui respecte l'AC « pas de trous au-delà de la fenêtre d'arrêt ».
 *
 * <p>Concurrence : le producteur actuel ({@code SimulatedMarketDataProvider#publishQuotes}) est
 * mono-thread ({@code @Scheduled}) et l'écouteur est synchrone — les écritures sont donc
 * sérialisées. La {@link ConcurrentHashMap} est conservée à titre défensif pour un futur producteur
 * asynchrone ou une source temps-réel multi-thread.
 */
@Component
class CandleAggregator {

  private final OhlcvRepository repository;
  private final Map<String, Bucket> openBuckets = new ConcurrentHashMap<>();

  CandleAggregator(OhlcvRepository repository) {
    this.repository = repository;
  }

  @EventListener
  void onQuoteTick(QuoteTick tick) {
    Instant minute = tick.at().truncatedTo(ChronoUnit.MINUTES);
    openBuckets.compute(
        tick.ticker(),
        (ticker, current) -> {
          if (current == null) {
            return Bucket.open(minute, tick.price());
          }
          if (minute.isAfter(current.bucket)) {
            /* La minute a changé : on flush le bucket clos et on en ouvre un nouveau. */
            flush(ticker, current);
            return Bucket.open(minute, tick.price());
          }
          current.addTick(tick.price());
          return current;
        });
  }

  private void flush(String ticker, Bucket bucket) {
    repository.upsert(
        ticker, bucket.bucket, bucket.open, bucket.high, bucket.low, bucket.close, bucket.volume);
  }

  /**
   * Bucket 1 minute en cours d'agrégation. {@code volume} compte le nombre de ticks reçus dans la
   * minute (proxy simple : {@link QuoteTick} ne porte pas de volume natif).
   */
  private static final class Bucket {
    final Instant bucket;
    final BigDecimal open;
    BigDecimal high;
    BigDecimal low;
    BigDecimal close;
    long volume;

    private Bucket(Instant bucket, BigDecimal price) {
      this.bucket = bucket;
      this.open = price;
      this.high = price;
      this.low = price;
      this.close = price;
      this.volume = 1L;
    }

    static Bucket open(Instant bucket, BigDecimal price) {
      return new Bucket(bucket, price);
    }

    void addTick(BigDecimal price) {
      if (price.compareTo(high) > 0) high = price;
      if (price.compareTo(low) < 0) low = price;
      close = price;
      volume++;
    }
  }
}
