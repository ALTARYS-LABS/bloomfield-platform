package com.bloomfield.terminal.model;

import java.math.BigDecimal;
import java.util.List;

public record MarketIndex(
    String name,
    BigDecimal value,
    BigDecimal change,
    BigDecimal changePercent,
    List<BigDecimal> sparklineData) {}
