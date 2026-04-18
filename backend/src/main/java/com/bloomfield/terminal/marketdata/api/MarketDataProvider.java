package com.bloomfield.terminal.marketdata.api;

import java.util.List;
import java.util.Optional;

/**
 * Replaceable market data source. The default implementation is {@code
 * SimulatedMarketDataProvider}; a real BRVM feed adapter can be swapped in by wiring a different
 * bean without touching callers.
 */
public interface MarketDataProvider {

  List<Quote> currentQuotes();

  Optional<TickerState> tickerState(String ticker);

  List<OhlcvCandle> history(String ticker, int days);

  List<MarketIndex> indices();

  List<OrderBookEntry> orderBook();
}
