package com.bloomfield.terminal.alerts.internal;

import com.bloomfield.terminal.alerts.domain.AlertRule;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

/** Dépôt Spring Data JDBC pour les règles d'alerte. */
public interface AlertRuleRepository extends CrudRepository<AlertRule, UUID> {

  /** Récupère toutes les règles activées pour un ticker donné. */
  @Query("SELECT * FROM alert_rules WHERE ticker = :ticker AND enabled = true")
  List<AlertRule> findEnabledByTicker(String ticker);

  /** Récupère toutes les règles d'un utilisateur. */
  @Query("SELECT * FROM alert_rules WHERE user_id = :userId ORDER BY created_at DESC")
  List<AlertRule> findByUserId(UUID userId);

  /** Récupère une règle spécifique (pour vérifier la propriété avant suppression). */
  Optional<AlertRule> findById(UUID id);
}
