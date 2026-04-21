package com.bloomfield.terminal.alerts.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** DTO pour afficher un événement d'alerte (déclenché). */
public record AlertEventView(
    UUID id, String ticker, BigDecimal price, Instant triggeredAt, boolean read) {}
