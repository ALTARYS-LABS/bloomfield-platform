package com.bloomfield.terminal.marketdata.domain;

import java.math.*;

public record TickerState(
    String name,
    String sector,
    BigDecimal openPrice,
    BigDecimal price,
    BigDecimal high,
    BigDecimal low,
    long volume,
    BigDecimal marketCap,
    BigDecimal per,
    BigDecimal dividendYield) {}
