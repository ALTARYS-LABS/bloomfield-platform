package com.bloomfield.terminal.marketdata.internal;

import com.bloomfield.terminal.marketdata.config.MarketIndicesProperties;
import com.bloomfield.terminal.marketdata.domain.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class MarketDataSimulator {

  private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

  private final SimpMessagingTemplate messagingTemplate;
  private final BigDecimal compositeBase;
  private final BigDecimal brvm10Base;
  private final Map<String, TickerState> tickers = new ConcurrentHashMap<>();
  private final List<BigDecimal> compositeHistory = new ArrayList<>();
  private final List<BigDecimal> brvm10History = new ArrayList<>();
  private BigDecimal compositeValue;
  private BigDecimal brvm10Value;

  public MarketDataSimulator(
      SimpMessagingTemplate messagingTemplate, MarketIndicesProperties indicesProperties) {
    this.messagingTemplate = messagingTemplate;
    this.compositeBase = indicesProperties.compositeBase();
    this.brvm10Base = indicesProperties.brvm10Base();
    this.compositeValue = compositeBase;
    this.brvm10Value = brvm10Base;
    initTickers();
  }

  private void initTickers() {
    addTicker("SGBCI", "Société Générale CI", "Finance", 12500, 1250e9, 12.5, 4.2);
    addTicker("ONTBF", "ONATEL Burkina Faso", "Télécoms", 4200, 420e9, 10.8, 5.1);
    addTicker("SNTS", "Sonatel", "Télécoms", 18900, 1890e9, 14.2, 3.8);
    addTicker("SIBC", "SIB Côte d'Ivoire", "Finance", 5100, 510e9, 9.5, 6.0);
    addTicker("TTLS", "Total Sénégal", "Énergie", 2350, 235e9, 11.0, 4.5);
    addTicker("ECOC", "Ecobank CI", "Finance", 7800, 780e9, 8.7, 5.5);
    addTicker("SDCC", "SDC Côte d'Ivoire", "Industrie", 6200, 620e9, 13.1, 3.2);
    addTicker("PALC", "Palm CI", "Agriculture", 4900, 490e9, 15.3, 2.8);
    addTicker("CABC", "CAB Côte d'Ivoire", "Distribution", 1150, 115e9, 7.9, 6.5);
    addTicker("BOAB", "BOA Bénin", "Finance", 5600, 560e9, 10.2, 4.8);
  }

  private void addTicker(
      String ticker,
      String name,
      String sector,
      double price,
      double marketCap,
      double per,
      double dividendYield) {
    BigDecimal bdPrice = BigDecimal.valueOf(price).setScale(2, RoundingMode.HALF_UP);
    tickers.put(
        ticker,
        new TickerState(
            name,
            sector,
            bdPrice,
            bdPrice,
            bdPrice,
            bdPrice,
            0,
            BigDecimal.valueOf(marketCap),
            BigDecimal.valueOf(per),
            BigDecimal.valueOf(dividendYield)));
  }

  @Scheduled(
      fixedDelayString =
          "#{T(java.util.concurrent.ThreadLocalRandom).current().nextLong(1000, 2001)}")
  void publishQuotes() {
    var random = ThreadLocalRandom.current();
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
      BigDecimal change = newPrice.subtract(state.openPrice()).setScale(2, RoundingMode.HALF_UP);
      BigDecimal changePct =
          change.multiply(ONE_HUNDRED).divide(state.openPrice(), 2, RoundingMode.HALF_UP);

      TickerState updated =
          new TickerState(
              state.name(),
              state.sector(),
              state.openPrice(),
              newPrice,
              newHigh,
              newLow,
              newVolume,
              state.marketCap(),
              state.per(),
              state.dividendYield());
      tickers.put(ticker, updated);

      quotes.add(
          new Quote(
              ticker,
              state.name(),
              state.sector(),
              newPrice,
              state.openPrice(),
              newHigh,
              newLow,
              state.openPrice(),
              newVolume,
              change,
              changePct,
              System.currentTimeMillis()));
    }
    messagingTemplate.convertAndSend("/topic/brvm/quotes", quotes);
  }

  @Scheduled(fixedDelay = 2000)
  void publishOrderBook() {
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
    messagingTemplate.convertAndSend("/topic/brvm/orderbook", books);
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

    List<MarketIndex> indices = getMarketIndices();
    messagingTemplate.convertAndSend("/topic/brvm/indices", indices);
  }

  private List<MarketIndex> getMarketIndices() {
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

  public List<Map<String, Object>> generateHistory(String ticker, int days) {
    TickerState state = tickers.get(ticker);
    if (state == null) return List.of();

    var random = new Random(ticker.hashCode());
    List<Map<String, Object>> history = new ArrayList<>();
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

      Map<String, Object> candle = new LinkedHashMap<>();
      candle.put("time", (now - (long) i * 86400000L) / 1000);
      candle.put("open", open);
      candle.put("high", high);
      candle.put("low", low);
      candle.put("close", close);
      candle.put("volume", volume);

      history.add(candle);
      price = close;
    }
    return history;
  }

  public TickerState getTickerState(String ticker) {
    return tickers.get(ticker);
  }
}
