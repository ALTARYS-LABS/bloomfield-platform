package com.bloomfield.terminal.portfolio.api.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.math.BigDecimal;

// BigDecimal sérialisé en chaîne pour éviter la perte de précision côté JavaScript
// (Number IEEE-754 ne peut pas représenter exactement > 15 chiffres significatifs).
// Appliqué seulement aux DTOs portefeuille pour ne pas perturber les quotes market-data existantes.
public record PositionView(
    String ticker,
    @JsonSerialize(using = ToStringSerializer.class) BigDecimal quantity,
    @JsonSerialize(using = ToStringSerializer.class) BigDecimal avgCost,
    @JsonSerialize(using = ToStringSerializer.class) BigDecimal currentPrice,
    @JsonSerialize(using = ToStringSerializer.class) BigDecimal marketValue,
    @JsonSerialize(using = ToStringSerializer.class) BigDecimal unrealizedPnl,
    @JsonSerialize(using = ToStringSerializer.class) BigDecimal unrealizedPnlPercent) {}
