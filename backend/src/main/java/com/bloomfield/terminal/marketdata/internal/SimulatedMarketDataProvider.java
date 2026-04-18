package com.bloomfield.terminal.marketdata.internal;

import com.bloomfield.terminal.marketdata.api.MarketDataProvider;
import com.bloomfield.terminal.marketdata.api.MarketIndex;
import com.bloomfield.terminal.marketdata.api.OhlcvCandle;
import com.bloomfield.terminal.marketdata.api.OrderBookEntry;
import com.bloomfield.terminal.marketdata.api.Quote;
import com.bloomfield.terminal.marketdata.api.TickerState;
import com.bloomfield.terminal.marketdata.config.MarketIndicesProperties;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * In-memory simulator implementing {@link MarketDataProvider}. Seeded from {@code
 * data/brvm-tickers.yml} via {@link TickerSeedLoader}; state mutates on a scheduled tick and is
 * broadcast over STOMP. A real feed adapter replaces this bean without touching controllers.
 */
@Service
class SimulatedMarketDataProvider implements MarketDataProvider {

  private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

  private final SimpMessagingTemplate messagingTemplate;
  private final BigDecimal compositeBase;
  private final BigDecimal brvm10Base;
  private final Map<String, TickerState> tickers = new ConcurrentHashMap<>();
  private final List<BigDecimal> compositeHistory = new ArrayList<>();
  private final List<BigDecimal> brvm10History = new ArrayList<>();
  private BigDecimal compositeValue;
  private BigDecimal brvm10Value;

  SimulatedMarketDataProvider(
      SimpMessagingTemplate messagingTemplate,
      MarketIndicesProperties indicesProperties,
      TickerSeedLoader seedLoader) {
    this.messagingTemplate = messagingTemplate;
    this.compositeBase = indicesProperties.compositeBase();
    this.brvm10Base = indicesProperties.brvm10Base();
    this.compositeValue = compositeBase;
    this.brvm10Value = brvm10Base;
    seedLoader.seeds().forEach(this::addTicker);
  }

  private void addTicker(TickerSeed seed) {
    BigDecimal openPrice = seed.openPrice().setScale(2, RoundingMode.HALF_UP);
    tickers.put(
        seed.ticker(),
        new TickerState(
            seed.name(),
            seed.sector(),
            seed.type(),
            openPrice,
            openPrice,
            openPrice,
            openPrice,
            0,
            seed.marketCap(),
            seed.per(),
            seed.dividendYield()));
  }

  @Override
  public List<Quote> currentQuotes() {
    long now = System.currentTimeMillis();
    return tickers.entrySet().stream().map(e -> toQuote(e.getKey(), e.getValue(), now)).toList();
  }

  @Override
  public Optional<TickerState> tickerState(String ticker) {
    return Optional.ofNullable(tickers.get(ticker));
  }

  @Override
  public List<OhlcvCandle> history(String ticker, int days) {
    TickerState state = tickers.get(ticker);
    if (state == null) return List.of();

    var random = new Random(ticker.hashCode());
    List<OhlcvCandle> history = new ArrayList<>();
    BigDecimal price =
        state.openPrice().multiply(BigDecimal.valueOf(0.95)).setScale(2, RoundingMode.HALF_UP);
    long now = System.currentTimeMillis();

    for (int i = days; i >= 0; i--) {
      BigDecimal open = price;
      BigDecimal close =
          open.multiply(BigDecimal.ONE.add(BigDecimal.valueOf(random.nextDouble(-0.02, 0.02))))
              .setScale(2, RoundingMode.HALF_UP);
      BigDecimal high =
          open.max(close)
              .multiply(BigDecimal.ONE.add(BigDecimal.valueOf(random.nextDouble(0, 0.01))))
              .setScale(2, RoundingMode.HALF_UP);
      BigDecimal low =
          open.min(close)
              .multiply(BigDecimal.ONE.subtract(BigDecimal.valueOf(random.nextDouble(0, 0.01))))
              .setScale(2, RoundingMode.HALF_UP);
      long volume = random.nextLong(1000, 50000);
      long time = (now - (long) i * 86_400_000L) / 1000L;

      history.add(new OhlcvCandle(time, open, high, low, close, volume));
      price = close;
    }
    return history;
  }

  @Override
  public List<MarketIndex> indices() {
    return buildIndices();
  }

  @Override
  public List<OrderBookEntry> orderBook() {
    return buildOrderBook();
  }

  @Scheduled(
      fixedDelayString =
          "#{T(java.util.concurrent.ThreadLocalRandom).current().nextLong(1000, 2001)}")
  void publishQuotes() {
    var random = ThreadLocalRandom.current();
    long now = System.currentTimeMillis();
    List<Quote> quotes = new ArrayList<>();

    for (var entry : tickers.entrySet()) {
      String ticker = entry.getKey();
      TickerState state = entry.getValue();

      BigDecimal variation =
          BigDecimal.ONE.add(BigDecimal.valueOf(random.nextDouble(-0.003, 0.003)));
      BigDecimal newPrice = state.price().multiply(variation).setScale(2, RoundingMode.HALF_UP);
      BigDecimal newHigh = state.high().max(newPrice);
      BigDecimal newLow = state.low().min(newPrice);
      long newVolume = state.volume() + random.nextLong(10, 500);

      TickerState updated =
          new TickerState(
              state.name(),
              state.sector(),
              state.type(),
              state.openPrice(),
              newPrice,
              newHigh,
              newLow,
              newVolume,
              state.marketCap(),
              state.per(),
              state.dividendYield());
      tickers.put(ticker, updated);

      quotes.add(toQuote(ticker, updated, now));
    }
    messagingTemplate.convertAndSend("/topic/brvm/quotes", quotes);
  }

  @Scheduled(fixedDelay = 2000)
  void publishOrderBook() {
    messagingTemplate.convertAndSend("/topic/brvm/orderbook", buildOrderBook());
  }

  @Scheduled(fixedDelay = 3000)
  void publishIndices() {
    var random = ThreadLocalRandom.current();

    BigDecimal compositeVariation =
        BigDecimal.ONE.add(BigDecimal.valueOf(random.nextDouble(-0.001, 0.001)));
    compositeValue = compositeValue.multiply(compositeVariation).setScale(2, RoundingMode.HALF_UP);
    compositeHistory.add(compositeValue);
    if (compositeHistory.size() > 20) compositeHistory.removeFirst();

    BigDecimal brvm10Variation =
        BigDecimal.ONE.add(BigDecimal.valueOf(random.nextDouble(-0.001, 0.001)));
    brvm10Value = brvm10Value.multiply(brvm10Variation).setScale(2, RoundingMode.HALF_UP);
    brvm10History.add(brvm10Value);
    if (brvm10History.size() > 20) brvm10History.removeFirst();

    messagingTemplate.convertAndSend("/topic/brvm/indices", buildIndices());
  }

  private Quote toQuote(String ticker, TickerState state, long timestamp) {
    BigDecimal change = state.price().subtract(state.openPrice()).setScale(2, RoundingMode.HALF_UP);
    BigDecimal changePercent =
        change.multiply(ONE_HUNDRED).divide(state.openPrice(), 2, RoundingMode.HALF_UP);
    return new Quote(
        ticker,
        state.name(),
        state.sector(),
        state.type(),
        state.price(),
        state.openPrice(),
        state.high(),
        state.low(),
        state.openPrice(),
        state.volume(),
        change,
        changePercent,
        timestamp);
  }

  private List<MarketIndex> buildIndices() {
    BigDecimal compositeChange =
        compositeValue.subtract(compositeBase).setScale(2, RoundingMode.HALF_UP);
    BigDecimal brvm10Change = brvm10Value.subtract(brvm10Base).setScale(2, RoundingMode.HALF_UP);

    BigDecimal compositePct =
        compositeChange.multiply(ONE_HUNDRED).divide(compositeBase, 2, RoundingMode.HALF_UP);
    BigDecimal brvm10Pct =
        brvm10Change.multiply(ONE_HUNDRED).divide(brvm10Base, 2, RoundingMode.HALF_UP);

    return List.of(
        new MarketIndex(
            "BRVM Composite",
            compositeValue,
            compositeChange,
            compositePct,
            new ArrayList<>(compositeHistory)),
        new MarketIndex(
            "BRVM 10", brvm10Value, brvm10Change, brvm10Pct, new ArrayList<>(brvm10History)));
  }

  private List<OrderBookEntry> buildOrderBook() {
    var random = ThreadLocalRandom.current();
    List<OrderBookEntry> books = new ArrayList<>();

    for (var entry : tickers.entrySet()) {
      BigDecimal price = entry.getValue().price();
      List<OrderBookEntry.Level> bids = new ArrayList<>();
      List<OrderBookEntry.Level> asks = new ArrayList<>();

      for (int i = 0; i < 5; i++) {
        BigDecimal spread = price.multiply(BigDecimal.valueOf(0.001 * (i + 1)));
        bids.add(
            new OrderBookEntry.Level(
                price.subtract(spread).setScale(2, RoundingMode.HALF_UP),
                random.nextInt(50, 2000)));
        asks.add(
            new OrderBookEntry.Level(
                price.add(spread).setScale(2, RoundingMode.HALF_UP), random.nextInt(50, 2000)));
      }
      books.add(new OrderBookEntry(entry.getKey(), bids, asks));
    }
    return books;
  }
}
