package com.bloomfield.terminal.marketdata.internal;

import java.time.LocalDate;

/**
 * Intervalle de dates inclusif côté démarrage et fin. Utilisé comme type de retour du {@link
 * MissingRangeResolver} et des découpages par fenêtre maximale côté {@link HistoricalCandleLoader}.
 */
record DateRange(LocalDate start, LocalDate end) {
  DateRange {
    if (end.isBefore(start)) {
      throw new IllegalArgumentException(
          "DateRange end (" + end + ") before start (" + start + ")");
    }
  }
}
