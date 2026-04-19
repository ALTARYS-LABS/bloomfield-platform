package com.bloomfield.terminal.marketdata.web;

import com.bloomfield.terminal.marketdata.api.CandleInterval;
import com.bloomfield.terminal.marketdata.api.OhlcvCandle;
import com.bloomfield.terminal.marketdata.internal.OhlcvRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API de bougies OHLCV historiques alimentée par l'hypertable TimescaleDB.
 *
 * <p>Contrat verrouillé : lorsque {@code from} / {@code to} et {@code limit} sont combinés, on
 * renvoie les {@code limit} bougies les plus récentes de la fenêtre, triées par ordre chronologique
 * ascendant (format attendu par lightweight-charts). Si {@code from} / {@code to} sont omis, la
 * fenêtre par défaut est « les {@code limit} derniers buckets » calculée à partir de la taille de
 * l'intervalle (ex. 200 bougies 1m = ~3h20, 200 bougies 1j = ~6 mois).
 */
@RestController
@RequestMapping("/api/brvm")
record CandleController(OhlcvRepository repository) {

  /** Nombre maximum de bougies renvoyées quand {@code limit} est omis. */
  private static final int DEFAULT_LIMIT = 200;

  /** Plafond dur pour éviter qu'un client demande un dump complet de l'hypertable. */
  private static final int MAX_LIMIT = 1000;

  @GetMapping("/candles/{ticker}")
  ResponseEntity<List<OhlcvCandle>> getCandles(
      @PathVariable String ticker,
      @RequestParam(name = "interval", defaultValue = "1m") String interval,
      @RequestParam(name = "from", required = false) Instant from,
      @RequestParam(name = "to", required = false) Instant to,
      @RequestParam(name = "limit", required = false) Integer limit) {

    var parsedInterval = CandleInterval.fromCode(interval);
    if (parsedInterval.isEmpty()) {
      return ResponseEntity.badRequest().build();
    }
    var resolved = parsedInterval.get();

    int effectiveLimit = Math.min(MAX_LIMIT, limit == null || limit <= 0 ? DEFAULT_LIMIT : limit);

    Instant effectiveTo = to != null ? to : Instant.now();
    Instant effectiveFrom =
        from != null ? from : defaultFrom(effectiveTo, resolved, effectiveLimit);

    /* La requête SQL applique un DESC LIMIT puis un tri ASC final pour renvoyer les plus récentes
     * bougies de la fenêtre dans l'ordre chronologique. */
    List<OhlcvCandle> candles =
        repository.findRange(
            ticker.toUpperCase(), resolved, effectiveFrom, effectiveTo, effectiveLimit);
    return ResponseEntity.ok(candles);
  }

  /**
   * Calcule la borne basse par défaut : {@code to - limit * interval}. Laisse une marge 2x pour
   * absorber les trous (trous de marché, redémarrages) et tenir les {@code limit} bougies
   * demandées.
   */
  private static Instant defaultFrom(Instant to, CandleInterval interval, int limit) {
    long seconds = interval.size().toSeconds() * limit * 2L;
    return to.minus(seconds, ChronoUnit.SECONDS);
  }
}
