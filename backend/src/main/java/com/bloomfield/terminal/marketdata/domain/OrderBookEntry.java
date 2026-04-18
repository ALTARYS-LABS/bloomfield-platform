package com.bloomfield.terminal.marketdata.domain;

import java.math.BigDecimal;
import java.util.List;

public record OrderBookEntry(String ticker, List<Level> bids, List<Level> asks) {
  public record Level(BigDecimal price, int quantity) {}
}
