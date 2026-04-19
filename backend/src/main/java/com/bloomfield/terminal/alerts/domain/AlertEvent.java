package com.bloomfield.terminal.alerts.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Événement d'alerte déclenché quand une règle se réalise. Peut rester avec delivered_at = null
 * jusqu'à ce que l'utilisateur se reconnecte.
 */
@Table("alert_events")
public record AlertEvent(
    @Id UUID id,
    UUID ruleId,
    UUID userId,
    String ticker,
    Instant triggeredAt,
    BigDecimal price,
    Instant deliveredAt,
    Instant readAt) {}
