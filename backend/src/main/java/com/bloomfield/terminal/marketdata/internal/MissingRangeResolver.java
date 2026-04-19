package com.bloomfield.terminal.marketdata.internal;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Identifie, pour un intervalle {@code [from, to]} demandé et l'ensemble des jours déjà présents en
 * base, les sous-intervalles manquants à rapatrier. Les samedis et dimanches sont exclus : la BRVM
 * ne cote pas le week-end, donc ces jours ne doivent jamais déclencher d'appel amont.
 *
 * <p>Note jours fériés : les jours fériés BRVM apparaîtront comme « manquants » à chaque passage
 * (Sikafinance retourne alors une liste vide). Accepté pour la démo, à raffiner plus tard via une
 * table de couverture dédiée si le volume d'appels devient un problème.
 */
final class MissingRangeResolver {

  private MissingRangeResolver() {}

  /**
   * Retourne la liste des intervalles contigus de jours ouvrés manquants dans {@code [from, to]},
   * dans l'ordre chronologique.
   */
  static List<DateRange> resolve(LocalDate from, LocalDate to, Set<LocalDate> existing) {
    if (to.isBefore(from)) {
      return List.of();
    }
    List<DateRange> ranges = new ArrayList<>();
    LocalDate runStart = null;
    LocalDate previous = null;
    for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
      if (isWeekend(day) || existing.contains(day)) {
        if (runStart != null) {
          ranges.add(new DateRange(runStart, previous));
          runStart = null;
          previous = null;
        }
        continue;
      }
      if (runStart == null) {
        runStart = day;
      }
      previous = day;
    }
    if (runStart != null) {
      ranges.add(new DateRange(runStart, previous));
    }
    return List.copyOf(ranges);
  }

  /** Découpe un intervalle en sous-fenêtres d'au plus {@code maxDays} jours inclus. */
  static List<DateRange> chunk(DateRange range, int maxDays) {
    if (maxDays <= 0) {
      throw new IllegalArgumentException("maxDays must be positive");
    }
    List<DateRange> chunks = new ArrayList<>();
    LocalDate cursor = range.start();
    while (!cursor.isAfter(range.end())) {
      LocalDate end = cursor.plusDays(maxDays - 1L);
      if (end.isAfter(range.end())) {
        end = range.end();
      }
      chunks.add(new DateRange(cursor, end));
      cursor = end.plusDays(1);
    }
    return List.copyOf(chunks);
  }

  private static boolean isWeekend(LocalDate day) {
    DayOfWeek dow = day.getDayOfWeek();
    return dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
  }
}
