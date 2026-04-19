package com.bloomfield.terminal.marketdata.api;

import java.util.List;
import java.util.Optional;

/**
 * Replaceable market data source. The default implementation is {@code
 * SimulatedMarketDataProvider}; a real BRVM feed adapter can be swapped in by wiring a different
 * bean without touching callers.
 *
 * <p>Historical candles are no longer served by this interface since STORY-008: the chart-friendly
 * API reads directly from the {@code ohlcv} TimescaleDB hypertable through {@code OhlcvRepository}
 * and {@code CandleController}.
 */
public interface MarketDataProvider {

  List<Quote> currentQuotes();

  Optional<TickerState> tickerState(String ticker);

  List<MarketIndex> indices();

  List<OrderBookEntry> orderBook();
}
