package com.bloomfield.terminal.alerts.domain;

/** Opérateur de comparaison pour une règle d'alerte. */
public enum ThresholdOperator {
  /** Le prix dépasse le seuil */
  ABOVE,
  /** Le prix descend sous le seuil */
  BELOW,
  /** Le prix croise le seuil à la hausse */
  CROSSES_UP,
  /** Le prix croise le seuil à la baisse */
  CROSSES_DOWN
}
