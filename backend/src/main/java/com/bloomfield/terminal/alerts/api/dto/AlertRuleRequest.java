package com.bloomfield.terminal.alerts.api.dto;

import com.bloomfield.terminal.alerts.domain.ThresholdOperator;
import java.math.BigDecimal;

/** DTO pour créer une nouvelle règle d'alerte. */
public record AlertRuleRequest(String ticker, ThresholdOperator operator, BigDecimal threshold) {}
