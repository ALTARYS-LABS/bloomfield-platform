package com.bloomfield.terminal.model;

import java.util.List;

public record MarketIndex(
        String name,
        double value,
        double change,
        double changePercent,
        List<Double> sparklineData
) {}
