package com.bloomfield.terminal.marketdata.api;

import java.math.BigDecimal;

public record TickerState(
    String name,
    String sector,
    SecurityType type,
    BigDecimal openPrice,
    BigDecimal price,
    BigDecimal high,
    BigDecimal low,
    long volume,
    BigDecimal marketCap,
    BigDecimal per,
    BigDecimal dividendYield) {}
