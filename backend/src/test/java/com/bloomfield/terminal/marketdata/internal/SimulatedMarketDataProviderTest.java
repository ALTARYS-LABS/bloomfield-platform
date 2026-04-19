package com.bloomfield.terminal.marketdata.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.bloomfield.terminal.marketdata.api.Quote;
import com.bloomfield.terminal.marketdata.api.SecurityType;
import com.bloomfield.terminal.marketdata.config.MarketIndicesProperties;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;

class SimulatedMarketDataProviderTest {

  private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

  private static SimulatedMarketDataProvider buildProvider(
      SimpMessagingTemplate template, ApplicationEventPublisher eventPublisher) {
    var seeds =
        List.of(
            new TickerSeed(
                "ALPHA",
                "Alpha Equity",
                "Finance",
                SecurityType.EQUITY,
                new BigDecimal("10000.00"),
                new BigDecimal("100000000000"),
                new BigDecimal("12.5"),
                new BigDecimal("4.0")),
            new TickerSeed(
                "BETA",
                "Beta Bond",
                "Obligations",
                SecurityType.BOND,
                new BigDecimal("10100.00"),
                new BigDecimal("50000000000"),
                new BigDecimal("0.0"),
                new BigDecimal("6.25")));
    var indices = new MarketIndicesProperties(new BigDecimal("234.56"), new BigDecimal("178.23"));
    return new SimulatedMarketDataProvider(
        template, eventPublisher, indices, new TickerSeedLoader(seeds));
  }

  @Test
  void publishQuotesProducesCoherentOhlcAndAccuratePercentChange() {
    var template = mock(SimpMessagingTemplate.class);
    var provider = buildProvider(template, mock(ApplicationEventPublisher.class));

    // Run several ticks so price drifts away from open, giving meaningful high/low spread.
    for (int i = 0; i < 50; i++) {
      provider.publishQuotes();
    }

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Quote>> captor = ArgumentCaptor.forClass(List.class);
    verify(template, atLeast(50))
        .convertAndSend(eq("/topic/brvm/quotes"), (Object) captor.capture());

    List<Quote> lastBatch = captor.getValue();
    assertThat(lastBatch).hasSize(2);

    for (Quote q : lastBatch) {
      assertThat(q.high())
          .as("high >= max(open, price) for %s", q.ticker())
          .isGreaterThanOrEqualTo(q.price().max(q.open()));
      assertThat(q.low())
          .as("low <= min(open, price) for %s", q.ticker())
          .isLessThanOrEqualTo(q.price().min(q.open()));
      assertThat(q.open())
          .as("close mirrors open in this simulator (no intraday close yet)")
          .isEqualByComparingTo(q.close());

      BigDecimal expectedChange = q.price().subtract(q.open()).setScale(2, RoundingMode.HALF_UP);
      assertThat(q.change()).isEqualByComparingTo(expectedChange);

      BigDecimal expectedPct =
          expectedChange.multiply(HUNDRED).divide(q.open(), 2, RoundingMode.HALF_UP);
      assertThat(q.changePercent()).isEqualByComparingTo(expectedPct);
    }
  }

  @Test
  void currentQuotesReflectsSeedTypeWithZeroInitialMovement() {
    var provider =
        buildProvider(mock(SimpMessagingTemplate.class), mock(ApplicationEventPublisher.class));

    List<Quote> quotes = provider.currentQuotes();

    assertThat(quotes).hasSize(2);
    Quote alpha = quotes.stream().filter(q -> q.ticker().equals("ALPHA")).findFirst().orElseThrow();
    Quote beta = quotes.stream().filter(q -> q.ticker().equals("BETA")).findFirst().orElseThrow();

    assertThat(alpha.type()).isEqualTo(SecurityType.EQUITY);
    assertThat(beta.type()).isEqualTo(SecurityType.BOND);
    assertThat(alpha.change()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(alpha.changePercent()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(beta.change()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(beta.changePercent()).isEqualByComparingTo(BigDecimal.ZERO);
  }

  @Test
  void tickerStateReturnsEmptyForUnknownTicker() {
    var provider =
        buildProvider(mock(SimpMessagingTemplate.class), mock(ApplicationEventPublisher.class));

    assertThat(provider.tickerState("UNKNOWN")).isEmpty();
    assertThat(provider.tickerState("ALPHA")).isPresent();
    assertThat(provider.tickerState("ALPHA").orElseThrow().type()).isEqualTo(SecurityType.EQUITY);
  }

  @Test
  void publishOrderBookBroadcastsFiveLevels() {
    var template = mock(SimpMessagingTemplate.class);
    var provider = buildProvider(template, mock(ApplicationEventPublisher.class));

    provider.publishOrderBook();

    verify(template).convertAndSend(eq("/topic/brvm/orderbook"), any(Object.class));
    var books = provider.orderBook();
    assertThat(books).hasSize(2);
    books.forEach(
        b -> {
          assertThat(b.bids()).hasSize(5);
          assertThat(b.asks()).hasSize(5);
        });
  }
}
