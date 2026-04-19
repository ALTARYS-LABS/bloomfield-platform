package com.bloomfield.terminal.alerts.internal;

import java.math.BigDecimal;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache thread-safe des prix précédents par ticker pour évaluer les opérateurs CROSSES_UP et
 * CROSSES_DOWN.
 */
class PreviousPriceCache {

  private final ConcurrentHashMap<String, BigDecimal> cache = new ConcurrentHashMap<>();

  /** Enregistre le prix précédent pour un ticker et retourne le prix avant mise à jour. */
  BigDecimal updateAndGetPrevious(String ticker, BigDecimal newPrice) {
    return cache.put(ticker, newPrice);
  }

  /** Récupère le prix précédent sans mise à jour. */
  BigDecimal getPrevious(String ticker) {
    return cache.get(ticker);
  }
}
