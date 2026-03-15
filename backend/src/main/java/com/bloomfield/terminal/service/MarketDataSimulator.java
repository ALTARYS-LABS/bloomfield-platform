package com.bloomfield.terminal.service;

import com.bloomfield.terminal.model.MarketIndex;
import com.bloomfield.terminal.model.OrderBookEntry;
import com.bloomfield.terminal.model.Quote;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class MarketDataSimulator {

    private final SimpMessagingTemplate messagingTemplate;
    private final Map<String, TickerState> tickers = new ConcurrentHashMap<>();
    private final List<Double> compositeHistory = new ArrayList<>();
    private final List<Double> brvm10History = new ArrayList<>();
    private double compositeValue = 234.56;
    private double brvm10Value = 178.23;

    public record TickerState(
            String name, String sector, double openPrice,
            double price, double high, double low, long volume,
            double marketCap, double per, double dividendYield
    ) {}

    public MarketDataSimulator(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
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

    private void addTicker(String ticker, String name, String sector, double price,
                           double marketCap, double per, double dividendYield) {
        tickers.put(ticker, new TickerState(name, sector, price, price, price, price, 0,
                marketCap, per, dividendYield));
    }

    @Scheduled(fixedDelayString = "#{T(java.util.concurrent.ThreadLocalRandom).current().nextLong(1000, 2001)}")
    public void publishQuotes() {
        var random = ThreadLocalRandom.current();
        List<Quote> quotes = new ArrayList<>();

        for (var entry : tickers.entrySet()) {
            String ticker = entry.getKey();
            TickerState state = entry.getValue();

            double variation = 1.0 + (random.nextDouble(-0.003, 0.003));
            double newPrice = Math.round(state.price() * variation * 100.0) / 100.0;
            double newHigh = Math.max(state.high(), newPrice);
            double newLow = Math.min(state.low(), newPrice);
            long newVolume = state.volume() + random.nextLong(10, 500);
            double change = newPrice - state.openPrice();
            double changePct = (change / state.openPrice()) * 100.0;

            TickerState updated = new TickerState(
                    state.name(), state.sector(), state.openPrice(),
                    newPrice, newHigh, newLow, newVolume,
                    state.marketCap(), state.per(), state.dividendYield()
            );
            tickers.put(ticker, updated);

            quotes.add(new Quote(
                    ticker, state.name(), state.sector(),
                    newPrice, state.openPrice(), newHigh, newLow, state.openPrice(),
                    newVolume, Math.round(change * 100.0) / 100.0,
                    Math.round(changePct * 100.0) / 100.0,
                    System.currentTimeMillis()
            ));
        }
        messagingTemplate.convertAndSend("/topic/brvm/quotes", quotes);
    }

    @Scheduled(fixedDelay = 2000)
    public void publishOrderBook() {
        var random = ThreadLocalRandom.current();
        List<OrderBookEntry> books = new ArrayList<>();

        for (var entry : tickers.entrySet()) {
            double price = entry.getValue().price();
            List<OrderBookEntry.Level> bids = new ArrayList<>();
            List<OrderBookEntry.Level> asks = new ArrayList<>();

            for (int i = 0; i < 5; i++) {
                double spread = price * 0.001 * (i + 1);
                bids.add(new OrderBookEntry.Level(
                        Math.round((price - spread) * 100.0) / 100.0,
                        random.nextInt(50, 2000)));
                asks.add(new OrderBookEntry.Level(
                        Math.round((price + spread) * 100.0) / 100.0,
                        random.nextInt(50, 2000)));
            }
            books.add(new OrderBookEntry(entry.getKey(), bids, asks));
        }
        messagingTemplate.convertAndSend("/topic/brvm/orderbook", books);
    }

    @Scheduled(fixedDelay = 3000)
    public void publishIndices() {
        var random = ThreadLocalRandom.current();

        compositeValue *= 1.0 + random.nextDouble(-0.001, 0.001);
        compositeValue = Math.round(compositeValue * 100.0) / 100.0;
        compositeHistory.add(compositeValue);
        if (compositeHistory.size() > 20) compositeHistory.removeFirst();

        brvm10Value *= 1.0 + random.nextDouble(-0.001, 0.001);
        brvm10Value = Math.round(brvm10Value * 100.0) / 100.0;
        brvm10History.add(brvm10Value);
        if (brvm10History.size() > 20) brvm10History.removeFirst();

        double compositeChange = compositeValue - 234.56;
        double brvm10Change = brvm10Value - 178.23;

        List<MarketIndex> indices = List.of(
                new MarketIndex("BRVM Composite", compositeValue, Math.round(compositeChange * 100.0) / 100.0,
                        Math.round((compositeChange / 234.56) * 10000.0) / 100.0, new ArrayList<>(compositeHistory)),
                new MarketIndex("BRVM 10", brvm10Value, Math.round(brvm10Change * 100.0) / 100.0,
                        Math.round((brvm10Change / 178.23) * 10000.0) / 100.0, new ArrayList<>(brvm10History))
        );
        messagingTemplate.convertAndSend("/topic/brvm/indices", indices);
    }

    public List<Map<String, Object>> generateHistory(String ticker, int days) {
        TickerState state = tickers.get(ticker);
        if (state == null) return List.of();

        var random = new Random(ticker.hashCode());
        List<Map<String, Object>> history = new ArrayList<>();
        double price = state.openPrice() * 0.95;
        long now = System.currentTimeMillis();

        for (int i = days; i >= 0; i--) {
            double open = price;
            double close = open * (1.0 + random.nextDouble(-0.02, 0.02));
            double high = Math.max(open, close) * (1.0 + random.nextDouble(0, 0.01));
            double low = Math.min(open, close) * (1.0 - random.nextDouble(0, 0.01));
            long volume = random.nextLong(1000, 50000);

            Map<String, Object> candle = new LinkedHashMap<>();
            candle.put("time", (now - (long) i * 86400000L) / 1000);
            candle.put("open", Math.round(open * 100.0) / 100.0);
            candle.put("high", Math.round(high * 100.0) / 100.0);
            candle.put("low", Math.round(low * 100.0) / 100.0);
            candle.put("close", Math.round(close * 100.0) / 100.0);
            candle.put("volume", volume);

            history.add(candle);
            price = close;
        }
        return history;
    }

    public TickerState getTickerState(String ticker) {
        return tickers.get(ticker);
    }

    public Map<String, TickerState> getAllTickers() {
        return Collections.unmodifiableMap(tickers);
    }
}
