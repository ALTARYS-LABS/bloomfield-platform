package com.bloomfield.terminal.marketdata.api;

import java.math.BigDecimal;

public record Quote(
    String ticker,
    String name,
    String sector,
    SecurityType type,
    BigDecimal price,
    BigDecimal open,
    BigDecimal high,
    BigDecimal low,
    BigDecimal close,
    long volume,
    BigDecimal change,
    BigDecimal changePercent,
    long timestamp) {}
