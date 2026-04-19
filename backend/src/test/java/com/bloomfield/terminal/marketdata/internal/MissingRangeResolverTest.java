package com.bloomfield.terminal.marketdata.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MissingRangeResolverTest {

  // 2026-01-05 = Monday, 2026-01-09 = Friday, 2026-01-10 = Saturday.

  @Test
  void emptyDbReturnsFullWeekdayRun() {
    var from = LocalDate.of(2026, 1, 5);
    var to = LocalDate.of(2026, 1, 9);
    var missing = MissingRangeResolver.resolve(from, to, Set.of());
    assertThat(missing).containsExactly(new DateRange(from, to));
  }

  @Test
  void weekendsAreExcludedFromMissingRanges() {
    // Lundi-dimanche : seule la série Mon-Fri doit apparaître.
    var from = LocalDate.of(2026, 1, 5);
    var to = LocalDate.of(2026, 1, 11);
    var missing = MissingRangeResolver.resolve(from, to, Set.of());
    assertThat(missing)
        .containsExactly(new DateRange(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 9)));
  }

  @Test
  void existingDayBreaksRun() {
    var from = LocalDate.of(2026, 1, 5);
    var to = LocalDate.of(2026, 1, 9);
    var existing = Set.of(LocalDate.of(2026, 1, 7)); // mercredi déjà présent
    var missing = MissingRangeResolver.resolve(from, to, existing);
    assertThat(missing)
        .containsExactly(
            new DateRange(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 6)),
            new DateRange(LocalDate.of(2026, 1, 8), LocalDate.of(2026, 1, 9)));
  }

  @Test
  void allDaysPresentReturnsEmpty() {
    var from = LocalDate.of(2026, 1, 5);
    var to = LocalDate.of(2026, 1, 9);
    var existing =
        Set.of(
            LocalDate.of(2026, 1, 5),
            LocalDate.of(2026, 1, 6),
            LocalDate.of(2026, 1, 7),
            LocalDate.of(2026, 1, 8),
            LocalDate.of(2026, 1, 9));
    assertThat(MissingRangeResolver.resolve(from, to, existing)).isEmpty();
  }

  @Test
  void endBeforeStartReturnsEmpty() {
    var from = LocalDate.of(2026, 1, 9);
    var to = LocalDate.of(2026, 1, 5);
    assertThat(MissingRangeResolver.resolve(from, to, Set.of())).isEmpty();
  }

  @Test
  void chunkSplitsOnMaxWindowDays() {
    // 100 jours → avec maxDays=89 on obtient deux sous-fenêtres : 89 puis 11.
    var range = new DateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1).plusDays(99));
    var chunks = MissingRangeResolver.chunk(range, 89);
    assertThat(chunks).hasSize(2);
    assertThat(chunks.get(0).start()).isEqualTo(LocalDate.of(2026, 1, 1));
    assertThat(chunks.get(0).end()).isEqualTo(LocalDate.of(2026, 1, 1).plusDays(88));
    assertThat(chunks.get(1).start()).isEqualTo(LocalDate.of(2026, 1, 1).plusDays(89));
    assertThat(chunks.get(1).end()).isEqualTo(LocalDate.of(2026, 1, 1).plusDays(99));
  }

  @Test
  void chunkReturnsSingleWhenSmallerThanMax() {
    var range = new DateRange(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 9));
    assertThat(MissingRangeResolver.chunk(range, 89)).containsExactly(range);
  }

  @Test
  void chunkThreeWayOnTwoHundredFiftyDays() {
    var range = new DateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1).plusDays(249));
    assertThat(MissingRangeResolver.chunk(range, 89)).hasSize(3);
  }
}
