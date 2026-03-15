package com.bloomfield.terminal.model;

public record Quote(
        String ticker,
        String name,
        String sector,
        double price,
        double open,
        double high,
        double low,
        double close,
        long volume,
        double change,
        double changePercent,
        long timestamp
) {}
