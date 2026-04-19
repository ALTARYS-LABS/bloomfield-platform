package com.bloomfield.terminal.marketdata.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.bloomfield.terminal.marketdata.api.QuoteTick;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Test unitaire du {@link CandleAggregator}. On simule un flux de ticks à cheval sur plusieurs
 * minutes et on vérifie : (1) qu'un bucket n'est flushé qu'à la clôture de la minute, (2) que les
 * valeurs OHLC calculées correspondent au flux injecté.
 */
class CandleAggregatorTest {

  private static final Instant MINUTE_0 = Instant.parse("2025-01-01T09:00:00Z");
  private static final Instant MINUTE_1 = Instant.parse("2025-01-01T09:01:00Z");
  private static final Instant MINUTE_2 = Instant.parse("2025-01-01T09:02:00Z");

  @Test
  void bucketIsNotFlushedWhileMinuteIsOpen() {
    var repo = mock(OhlcvRepository.class);
    var aggregator = new CandleAggregator(repo);

    aggregator.onQuoteTick(new QuoteTick("SNTS", new BigDecimal("100.00"), MINUTE_0));
    aggregator.onQuoteTick(
        new QuoteTick("SNTS", new BigDecimal("101.00"), MINUTE_0.plusSeconds(30)));

    verify(repo, never()).upsert(any(), any(), any(), any(), any(), any(), anyLong());
  }

  @Test
  void bucketFlushesOnMinuteRollWithCorrectOhlc() {
    var repo = mock(OhlcvRepository.class);
    var aggregator = new CandleAggregator(repo);

    // Minute 0 : open=100, high=105, low=95, close=102, 4 ticks
    aggregator.onQuoteTick(new QuoteTick("SNTS", new BigDecimal("100.00"), MINUTE_0));
    aggregator.onQuoteTick(
        new QuoteTick("SNTS", new BigDecimal("105.00"), MINUTE_0.plusSeconds(15)));
    aggregator.onQuoteTick(
        new QuoteTick("SNTS", new BigDecimal("95.00"), MINUTE_0.plusSeconds(30)));
    aggregator.onQuoteTick(
        new QuoteTick("SNTS", new BigDecimal("102.00"), MINUTE_0.plusSeconds(45)));

    // Premier tick de la minute suivante : déclenche le flush du bucket minute 0.
    aggregator.onQuoteTick(new QuoteTick("SNTS", new BigDecimal("103.00"), MINUTE_1));

    var openCap = ArgumentCaptor.forClass(BigDecimal.class);
    var highCap = ArgumentCaptor.forClass(BigDecimal.class);
    var lowCap = ArgumentCaptor.forClass(BigDecimal.class);
    var closeCap = ArgumentCaptor.forClass(BigDecimal.class);
    var volumeCap = ArgumentCaptor.forClass(Long.class);
    verify(repo, times(1))
        .upsert(
            eq("SNTS"),
            eq(MINUTE_0),
            openCap.capture(),
            highCap.capture(),
            lowCap.capture(),
            closeCap.capture(),
            volumeCap.capture());

    assertThat(openCap.getValue()).isEqualByComparingTo("100.00");
    assertThat(highCap.getValue()).isEqualByComparingTo("105.00");
    assertThat(lowCap.getValue()).isEqualByComparingTo("95.00");
    assertThat(closeCap.getValue()).isEqualByComparingTo("102.00");
    assertThat(volumeCap.getValue()).isEqualTo(4L);
  }

  @Test
  void bucketsArePerTickerIndependent() {
    var repo = mock(OhlcvRepository.class);
    var aggregator = new CandleAggregator(repo);

    aggregator.onQuoteTick(new QuoteTick("SNTS", new BigDecimal("100.00"), MINUTE_0));
    aggregator.onQuoteTick(new QuoteTick("BOAC", new BigDecimal("50.00"), MINUTE_0));

    // Roll SNTS vers la minute 1 ; BOAC reste sur la minute 0.
    aggregator.onQuoteTick(new QuoteTick("SNTS", new BigDecimal("101.00"), MINUTE_1));

    verify(repo, times(1)).upsert(eq("SNTS"), eq(MINUTE_0), any(), any(), any(), any(), anyLong());
    verify(repo, never()).upsert(eq("BOAC"), any(), any(), any(), any(), any(), anyLong());
  }

  @Test
  void consecutiveMinuteRollsFlushEachClosedBucket() {
    var repo = mock(OhlcvRepository.class);
    var aggregator = new CandleAggregator(repo);

    aggregator.onQuoteTick(new QuoteTick("SNTS", new BigDecimal("100.00"), MINUTE_0));
    aggregator.onQuoteTick(new QuoteTick("SNTS", new BigDecimal("101.00"), MINUTE_1));
    aggregator.onQuoteTick(new QuoteTick("SNTS", new BigDecimal("102.00"), MINUTE_2));

    verify(repo, times(2)).upsert(eq("SNTS"), any(), any(), any(), any(), any(), anyLong());
  }
}
