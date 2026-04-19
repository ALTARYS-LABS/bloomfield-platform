package com.bloomfield.terminal.alerts.api.dto;

import com.bloomfield.terminal.alerts.domain.ThresholdOperator;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** DTO pour afficher une règle d'alerte. */
public record AlertRuleView(
    UUID id,
    String ticker,
    ThresholdOperator operator,
    BigDecimal threshold,
    boolean enabled,
    Instant createdAt) {}
