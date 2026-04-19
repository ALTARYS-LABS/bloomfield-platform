package com.bloomfield.terminal.marketdata.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests unitaires ciblés sur les helpers purs du seeder (pas de Spring, pas de DB). */
class SimulatedHistorySeederTest {

  @Test
  void tradingDaysEndingAtSkipsWeekendsAndReturnsAscendingOrder() {
    // Vendredi 2026-04-17.
    LocalDate friday = LocalDate.of(2026, 4, 17);
    List<LocalDate> days = SimulatedHistorySeeder.tradingDaysEndingAt(friday, 5);

    assertThat(days).hasSize(5);
    assertThat(days).isSorted();
    assertThat(days.getLast()).isEqualTo(friday);
    // Aucun samedi/dimanche dans la séquence retournée.
    assertThat(days)
        .allSatisfy(
            d -> assertThat(d.getDayOfWeek()).isNotIn(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY));
    // Lundi 2026-04-13 -> vendredi 2026-04-17 : 5 jours ouvrés contigus.
    assertThat(days.getFirst()).isEqualTo(LocalDate.of(2026, 4, 13));
  }

  @Test
  void tradingDaysEndingAtOnWeekendStartsFromPriorFriday() {
    // Dimanche 2026-04-19 : la fenêtre démarre à la dernière clôture ouvrée.
    LocalDate sunday = LocalDate.of(2026, 4, 19);
    List<LocalDate> days = SimulatedHistorySeeder.tradingDaysEndingAt(sunday, 1);

    assertThat(days).containsExactly(LocalDate.of(2026, 4, 17));
  }
}
