package com.bloomfield.terminal.alerts.internal;

import com.bloomfield.terminal.alerts.api.dto.AlertEventView;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

/**
 * Gère la reconnexion des utilisateurs : lorsqu'un utilisateur se reconnecte et s'abonne à
 * /user/queue/alerts, envoie tous les événements non livrés et les marque comme livrés.
 */
@Service
class ReconnectHandler {

  private final AlertEventRepository eventRepository;
  private final SimpMessagingTemplate messagingTemplate;

  ReconnectHandler(AlertEventRepository eventRepository, SimpMessagingTemplate messagingTemplate) {
    this.eventRepository = eventRepository;
    this.messagingTemplate = messagingTemplate;
  }

  /** Écoute les abonnements STOMP et traite les reconnexions aux alertes. */
  @org.springframework.context.event.EventListener
  @Transactional
  void onSessionSubscribe(SessionSubscribeEvent event) {
    StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
    String destination = accessor.getDestination();

    /* Vérifie que c'est bien une souscription à /user/queue/alerts */
    if (destination != null && destination.endsWith("/queue/alerts")) {
      Principal principal = accessor.getUser();
      if (principal != null) {
        try {
          UUID userId = UUID.fromString(principal.getName());
          flushUndeliveredEvents(userId);
        } catch (IllegalArgumentException e) {
          /* L'identité de l'utilisateur n'est pas un UUID valide ; ignorer */
        }
      }
    }
  }

  /** Envoie tous les événements non livrés d'un utilisateur et les marque comme livrés. */
  private void flushUndeliveredEvents(UUID userId) {
    List<com.bloomfield.terminal.alerts.domain.AlertEvent> events =
        eventRepository.findUndeliveredByUserId(userId);

    for (var event : events) {
      var eventView =
          new AlertEventView(event.id(), event.ticker(), event.price(), event.triggeredAt(), false);
      messagingTemplate.convertAndSendToUser(userId.toString(), "/queue/alerts", eventView);
    }

    /* Marque tous les événements comme livrés */
    eventRepository.markDeliveredByUserId(userId);
  }
}
