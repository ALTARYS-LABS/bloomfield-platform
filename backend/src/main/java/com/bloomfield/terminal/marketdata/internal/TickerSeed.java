package com.bloomfield.terminal.marketdata.internal;

import com.bloomfield.terminal.marketdata.api.SecurityType;
import java.math.BigDecimal;

/**
 * One entry from {@code data/brvm-tickers.yml}. Package-private: the YAML shape is an
 * implementation detail, callers see {@code TickerState} instead.
 */
record TickerSeed(
    String ticker,
    String name,
    String sector,
    SecurityType type,
    BigDecimal openPrice,
    BigDecimal marketCap,
    BigDecimal per,
    BigDecimal dividendYield) {}
