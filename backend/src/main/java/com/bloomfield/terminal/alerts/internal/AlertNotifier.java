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
record AlertNotifier(SimpMessagingTemplate messagingTemplate) {

  /**
   * Envoie une alerte à un utilisateur. Pour la démo, on utilise le UUID de l'utilisateur comme
   * identifiant STOMP. Best-effort : le message est perdu si l'utilisateur n'est pas connecté.
   */
  void sendAlertToUser(UUID userId, AlertEventView event) {
    // Envoi best-effort (perdu si l'utilisateur n'est pas connecté)
    messagingTemplate.convertAndSendToUser(userId.toString(), "/queue/alerts", event);
  }
}
