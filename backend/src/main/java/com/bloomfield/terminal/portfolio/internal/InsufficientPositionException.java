package com.bloomfield.terminal.portfolio.internal;

// Levée quand une vente dépasse la quantité détenue ou qu'aucune position n'existe.
class InsufficientPositionException extends RuntimeException {
  InsufficientPositionException(String ticker) {
    super("Quantité détenue insuffisante pour vendre " + ticker);
  }
}
