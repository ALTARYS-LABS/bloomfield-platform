package com.bloomfield.terminal.portfolio.api.dto;

import com.bloomfield.terminal.portfolio.api.Side;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

// Le prix d'exécution côté prototype est pris au dernier tick serveur : on ne le lit donc
// pas depuis la requête, seul le couple (ticker, side, quantity) est nécessaire.
public record SubmitTradeRequest(
    @NotBlank String ticker,
    @NotNull Side side,
    @NotNull @DecimalMin(value = "0.000001", inclusive = true) BigDecimal quantity) {}
