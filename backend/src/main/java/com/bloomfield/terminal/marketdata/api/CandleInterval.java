package com.bloomfield.terminal.marketdata.api;

import java.time.Duration;
import java.util.Optional;

/** Granularités d'agrégation exposées par l'API de bougies. */
public enum CandleInterval {
  ONE_MINUTE("1m", Duration.ofMinutes(1)),
  FIVE_MINUTES("5m", Duration.ofMinutes(5)),
  ONE_HOUR("1h", Duration.ofHours(1)),
  ONE_DAY("1d", Duration.ofDays(1));

  private final String code;
  private final Duration size;

  CandleInterval(String code, Duration size) {
    this.code = code;
    this.size = size;
  }

  public String code() {
    return code;
  }

  public Duration size() {
    return size;
  }

  /** Décode le paramètre {@code interval=1m|5m|1h|1d}. */
  public static Optional<CandleInterval> fromCode(String code) {
    if (code == null) return Optional.empty();
    for (var v : values()) {
      if (v.code.equalsIgnoreCase(code)) return Optional.of(v);
    }
    return Optional.empty();
  }
}
