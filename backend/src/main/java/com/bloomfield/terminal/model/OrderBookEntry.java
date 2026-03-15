package com.bloomfield.terminal.model;

import java.util.List;

public record OrderBookEntry(
        String ticker,
        List<Level> bids,
        List<Level> asks
) {
    public record Level(double price, int quantity) {}
}
