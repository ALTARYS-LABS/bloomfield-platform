package com.bloomfield.terminal.alerts.internal;

import com.bloomfield.terminal.alerts.api.dto.AlertEventView;
import java.util.UUID;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Envoie les alertes aux utilisateurs connectés via STOMP /user/queue/alerts. Si l'utilisateur
 * n'est pas connecté, le message est perdu ; ReconnectHandler gère la reconnexion.
 */
@Service
class AlertNotifier {

  private final SimpMessagingTemplate messagingTemplate;
  private final AlertEventRepository eventRepository;

  AlertNotifier(SimpMessagingTemplate messagingTemplate, AlertEventRepository eventRepository) {
    this.messagingTemplate = messagingTemplate;
    this.eventRepository = eventRepository;
  }

  /**
   * Envoie une alerte à un utilisateur (en mieux: par email/identifiant utilisateur). Pour la démo,
   * on utilise le UUID de l'utilisateur comme identifiant STOMP. Remarque: Cette approche suppose
   * que le ChannelInterceptor STOMP enregistre les sessions.
   */
  void sendAlertToUser(UUID userId, AlertEventView event) {
    /* Envoie un message best-effort (peut être perdu si l'utilisateur n'est pas connecté) */
    messagingTemplate.convertAndSendToUser(userId.toString(), "/queue/alerts", event);
  }
}
