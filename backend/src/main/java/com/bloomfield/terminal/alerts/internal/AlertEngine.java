package com.bloomfield.terminal.alerts.internal;

import com.bloomfield.terminal.alerts.api.dto.AlertEventView;
import com.bloomfield.terminal.alerts.domain.AlertEvent;
import com.bloomfield.terminal.alerts.domain.AlertRule;
import com.bloomfield.terminal.marketdata.api.QuoteTick;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Moteur d'alerte : écoute les événements de prix (QuoteTick) et évalue les règles. Sur
 * déclenchement, persiste un AlertEvent et notifie l'utilisateur via STOMP.
 */
@Service
class AlertEngine {

  private final AlertRuleRepository ruleRepository;
  private final AlertEventRepository eventRepository;
  private final AlertNotifier notifier;
  private final PreviousPriceCache priceCache;

  AlertEngine(
      AlertRuleRepository ruleRepository,
      AlertEventRepository eventRepository,
      AlertNotifier notifier,
      PreviousPriceCache priceCache) {
    this.ruleRepository = ruleRepository;
    this.eventRepository = eventRepository;
    this.notifier = notifier;
    this.priceCache = priceCache;
  }

  /** Écoute les événements QuoteTick et évalue toutes les règles pour ce ticker. */
  @ApplicationModuleListener
  @Transactional
  void onQuoteTick(QuoteTick tick) {
    /* Récupère toutes les règles activées pour ce ticker */
    List<AlertRule> rules = ruleRepository.findEnabledByTicker(tick.ticker());

    for (AlertRule rule : rules) {
      if (shouldFireRule(rule, tick.price())) {
        /* La règle se déclenche : persiste l'événement et notifie l'utilisateur */
        fireRule(rule, tick);
      }
    }

    /* Met à jour le cache des prix précédents pour les opérateurs CROSSES_* */
    priceCache.updateAndGetPrevious(tick.ticker(), tick.price());
  }

  /** Vérifie si une règle doit se déclencher en fonction du prix actuel et de l'opérateur. */
  private boolean shouldFireRule(AlertRule rule, BigDecimal currentPrice) {
    return switch (rule.operator()) {
      case ABOVE -> currentPrice.compareTo(rule.threshold()) >= 0;
      case BELOW -> currentPrice.compareTo(rule.threshold()) <= 0;
      case CROSSES_UP -> {
        BigDecimal prevPrice = priceCache.getPrevious(rule.ticker());
        yield prevPrice != null
            && prevPrice.compareTo(rule.threshold()) < 0
            && currentPrice.compareTo(rule.threshold()) >= 0;
      }
      case CROSSES_DOWN -> {
        BigDecimal prevPrice = priceCache.getPrevious(rule.ticker());
        yield prevPrice != null
            && prevPrice.compareTo(rule.threshold()) > 0
            && currentPrice.compareTo(rule.threshold()) <= 0;
      }
    };
  }

  /**
   * Déclenche une règle : crée un événement, désactive la règle (one-shot), et notifie
   * l'utilisateur.
   */
  private void fireRule(AlertRule rule, QuoteTick tick) {
    /* Crée et persiste l'événement d'alerte */
    var event =
        new AlertEvent(
            UUID.randomUUID(),
            rule.id(),
            rule.userId(),
            rule.ticker(),
            Instant.now(),
            tick.price(),
            null, /* delivered_at sera mis à jour par ReconnectHandler */
            null);
    eventRepository.save(event);

    /* Désactive la règle (comportement one-shot pour la démo) */
    var disabledRule =
        new AlertRule(
            rule.id(),
            rule.userId(),
            rule.ticker(),
            rule.operator(),
            rule.threshold(),
            false,
            rule.createdAt());
    ruleRepository.save(disabledRule);

    /* Envoie une notification en best-effort */
    var eventView =
        new AlertEventView(event.id(), event.ticker(), event.price(), event.triggeredAt(), false);
    notifier.sendAlertToUser(rule.userId(), eventView);
  }
}
