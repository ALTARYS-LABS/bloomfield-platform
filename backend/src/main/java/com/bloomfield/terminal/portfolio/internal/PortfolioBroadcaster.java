package com.bloomfield.terminal.portfolio.internal;

import java.util.UUID;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Pousse une mise à jour de portefeuille toutes les 2 s vers chaque utilisateur actuellement
// connecté sur un socket STOMP. On s'appuie sur SimpUserRegistry pour ne diffuser qu'aux
// clients présents : pas de travail inutile si personne n'écoute.
@Component
record PortfolioBroadcaster(
    SimpUserRegistry simpUserRegistry,
    SimpMessagingTemplate simpMessagingTemplate,
    PortfolioService portfolioService) {

  @Scheduled(fixedDelay = 2000)
  void broadcast() {
    for (var user : simpUserRegistry.getUsers()) {
      var principal = user.getName();
      if (principal == null) {
        continue;
      }
      UUID userId;
      try {
        userId = UUID.fromString(principal);
      } catch (IllegalArgumentException ex) {
        // Principal non-UUID (utilisateur anonyme market-data) : rien à pousser.
        continue;
      }
      // Le principal STOMP est l'UUID utilisateur (voir StompAuthChannelInterceptor).
      // convertAndSendToUser préfixe la destination par /user/<principal>.
      var summary = portfolioService.summaryFor(userId);
      simpMessagingTemplate.convertAndSendToUser(principal, "/queue/portfolio", summary);
    }
  }
}
