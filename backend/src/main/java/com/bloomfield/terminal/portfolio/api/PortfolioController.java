package com.bloomfield.terminal.portfolio.api;

import com.bloomfield.terminal.portfolio.api.dto.PortfolioSummary;
import com.bloomfield.terminal.portfolio.api.dto.SubmitTradeRequest;
import com.bloomfield.terminal.portfolio.api.dto.TradeView;
import com.bloomfield.terminal.portfolio.internal.PortfolioService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Toutes les routes exigent une authentification (configuré via SecurityConfig :
// anyRequest().authenticated()).
// Le sujet du JWT est l'UUID utilisateur : on l'utilise comme clé d'accès, jamais un paramètre de
// requête,
// ce qui empêche un utilisateur A de consulter le portefeuille d'un utilisateur B.
@RestController
@RequestMapping("/api/portfolio")
record PortfolioController(PortfolioService portfolioService) {

  @GetMapping
  PortfolioSummary mySummary(@AuthenticationPrincipal Jwt principal) {
    return portfolioService.summaryFor(UUID.fromString(principal.getSubject()));
  }

  @GetMapping("/trades")
  List<TradeView> myTrades(
      @AuthenticationPrincipal Jwt principal, @RequestParam(defaultValue = "50") int limit) {
    // Clamp défensif pour empêcher un client de forcer une requête massive.
    var safeLimit = Math.min(Math.max(limit, 1), 500);
    return portfolioService.recentTrades(UUID.fromString(principal.getSubject()), safeLimit);
  }

  @PostMapping("/trades")
  PortfolioSummary submitTrade(
      @AuthenticationPrincipal Jwt principal, @Valid @RequestBody SubmitTradeRequest request) {
    return portfolioService.submitTrade(UUID.fromString(principal.getSubject()), request);
  }
}
