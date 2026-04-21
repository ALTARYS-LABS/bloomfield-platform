package com.bloomfield.terminal.portfolio.api.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record PortfolioSummary(
    UUID id,
    String name,
    List<PositionView> positions,
    // Valeur marchande totale : somme des quantity * currentPrice.
    @JsonSerialize(using = ToStringSerializer.class) BigDecimal totalValue,
    // Base de coût : somme des quantity * avgCost.
    @JsonSerialize(using = ToStringSerializer.class) BigDecimal totalCost,
    @JsonSerialize(using = ToStringSerializer.class) BigDecimal unrealizedPnl,
    @JsonSerialize(using = ToStringSerializer.class) BigDecimal realizedPnl,
    @JsonSerialize(using = ToStringSerializer.class) BigDecimal unrealizedPnlPercent) {}
