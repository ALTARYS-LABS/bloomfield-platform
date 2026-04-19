package com.bloomfield.terminal.marketdata.api;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Événement publié par le fournisseur de données de marché à chaque tick de prix. Utilisé par
 * d'autres modules (alertes) pour évaluer les règles.
 */
public record QuoteTick(String ticker, BigDecimal price, Instant at) {}
