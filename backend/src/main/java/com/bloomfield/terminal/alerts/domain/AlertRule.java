package com.bloomfield.terminal.alerts.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Règle d'alerte définie par un utilisateur. Une fois déclenchée, la règle est désactivée
 * (comportement "one-shot" pour la démo v2).
 */
@Table("alert_rules")
public record AlertRule(
    @Id UUID id,
    UUID userId,
    String ticker,
    ThresholdOperator operator,
    BigDecimal threshold,
    boolean enabled,
    Instant createdAt) {}
