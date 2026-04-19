package com.bloomfield.terminal.portfolio.internal;

// Levée quand une requête de trade référence un ticker inconnu du MarketDataProvider.
// Le ExceptionHandler la traduit en 400 pour le client HTTP.
class UnknownTickerException extends RuntimeException {
  UnknownTickerException(String ticker) {
    super("Ticker inconnu : " + ticker);
  }
}
