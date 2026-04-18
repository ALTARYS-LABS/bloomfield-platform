package com.bloomfield.terminal.marketdata.domain;

import java.math.BigDecimal;

public record Quote(
    String ticker,
    String name,
    String sector,
    BigDecimal price,
    BigDecimal open,
    BigDecimal high,
    BigDecimal low,
    BigDecimal close,
    long volume,
    BigDecimal change,
    BigDecimal changePercent,
    long timestamp) {}
