package com.bloomfield.terminal.alerts.internal;

import com.bloomfield.terminal.alerts.domain.AlertEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

/** Dépôt Spring Data JDBC pour les événements d'alerte. */
public interface AlertEventRepository extends CrudRepository<AlertEvent, UUID> {

  /** Récupère les N derniers événements d'un utilisateur. */
  @Query(
      "SELECT * FROM alert_events WHERE user_id = :userId ORDER BY triggered_at DESC LIMIT :limit")
  List<AlertEvent> findRecentByUserId(UUID userId, int limit);

  /** Récupère les événements non livrés d'un utilisateur (pour la reconnexion). */
  @Query(
      "SELECT * FROM alert_events WHERE user_id = :userId AND delivered_at IS NULL "
          + "ORDER BY triggered_at ASC")
  List<AlertEvent> findUndeliveredByUserId(UUID userId);

  /**
   * Met à jour le champ delivered_at et read_at pour les événements non livrés d'un utilisateur.
   */
  @Modifying
  @Query(
      "UPDATE alert_events SET delivered_at = now() WHERE user_id = :userId AND delivered_at IS NULL")
  void markDeliveredByUserId(UUID userId);

  /** Marque un événement spécifique comme lu. */
  @Modifying
  @Query("UPDATE alert_events SET read_at = now() WHERE id = :eventId")
  void markAsRead(UUID eventId);
}
