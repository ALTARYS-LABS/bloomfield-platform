package com.bloomfield.terminal.portfolio.api.dto;

import com.bloomfield.terminal.portfolio.api.Side;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record TradeView(
    UUID id,
    String ticker,
    Side side,
    @JsonSerialize(using = ToStringSerializer.class) BigDecimal quantity,
    @JsonSerialize(using = ToStringSerializer.class) BigDecimal price,
    OffsetDateTime executedAt,
    @JsonSerialize(using = ToStringSerializer.class) BigDecimal realizedPnl) {}
