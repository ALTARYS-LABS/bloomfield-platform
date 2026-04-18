package com.bloomfield.terminal.marketdata.api;

import java.math.BigDecimal;

/**
 * Open-High-Low-Close-Volume candle. {@code time} is a Unix timestamp in seconds to match the
 * lightweight-charts series shape the frontend already consumes.
 */
public record OhlcvCandle(
    long time, BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close, long volume) {}
