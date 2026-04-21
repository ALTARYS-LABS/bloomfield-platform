package com.bloomfield.terminal.marketdata.internal;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Barre historique normalisée, prête à être insérée dans {@code ohlcv}. Le ticker porté ici est
 * déjà le code canonique BRVM (ex. {@code SGBC}), jamais le code Sikafinance ({@code SGBCI}).
 */
record HistoricalBar(
    String ticker,
    Instant bucket,
    BigDecimal open,
    BigDecimal high,
    BigDecimal low,
    BigDecimal close,
    long volume) {}
