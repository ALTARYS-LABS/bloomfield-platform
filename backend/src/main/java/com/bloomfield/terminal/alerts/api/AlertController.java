package com.bloomfield.terminal.alerts.api;

import com.bloomfield.terminal.alerts.api.dto.AlertEventView;
import com.bloomfield.terminal.alerts.api.dto.AlertRuleRequest;
import com.bloomfield.terminal.alerts.api.dto.AlertRuleView;
import com.bloomfield.terminal.alerts.domain.AlertRule;
import com.bloomfield.terminal.alerts.internal.AlertEventRepository;
import com.bloomfield.terminal.alerts.internal.AlertRuleRepository;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API REST pour la gestion des règles et événements d'alerte. Tous les endpoints sont protégés :
 * seules les alertes de l'utilisateur connecté sont accessibles.
 */
@RestController
@RequestMapping("/api/alerts")
record AlertController(AlertRuleRepository ruleRepository, AlertEventRepository eventRepository) {

  /** Liste toutes les règles d'alerte de l'utilisateur connecté. */
  @GetMapping("/rules")
  List<AlertRuleView> getRules(@AuthenticationPrincipal Jwt principal) {
    var userId = UUID.fromString(principal.getSubject());
    return ruleRepository.findByUserId(userId).stream()
        .map(
            rule ->
                new AlertRuleView(
                    rule.id(),
                    rule.ticker(),
                    rule.operator(),
                    rule.threshold(),
                    rule.enabled(),
                    rule.createdAt()))
        .toList();
  }

  /** Crée une nouvelle règle d'alerte pour l'utilisateur connecté. */
  @PostMapping("/rules")
  ResponseEntity<AlertRuleView> createRule(
      @AuthenticationPrincipal Jwt principal, @Valid @RequestBody AlertRuleRequest request) {
    var userId = UUID.fromString(principal.getSubject());
    var rule =
        AlertRule.newRule(
            UUID.randomUUID(),
            userId,
            request.ticker(),
            request.operator(),
            request.threshold(),
            true,
            Instant.now());
    var saved = ruleRepository.save(rule);

    var view =
        new AlertRuleView(
            saved.id(),
            saved.ticker(),
            saved.operator(),
            saved.threshold(),
            saved.enabled(),
            saved.createdAt());
    return ResponseEntity.status(HttpStatus.CREATED).body(view);
  }

  /** Supprime une règle d'alerte (404 si elle n'appartient pas à l'utilisateur). */
  @DeleteMapping("/rules/{id}")
  ResponseEntity<Void> deleteRule(@AuthenticationPrincipal Jwt principal, @PathVariable UUID id) {
    var userId = UUID.fromString(principal.getSubject());

    // Vérifie la propriété de la règle
    var rule = ruleRepository.findById(id);
    if (rule.isEmpty() || !rule.get().userId().equals(userId)) {
      return ResponseEntity.notFound().build();
    }

    ruleRepository.deleteById(id);
    return ResponseEntity.noContent().build();
  }

  /** Liste les derniers événements d'alerte de l'utilisateur connecté. */
  @GetMapping("/events")
  List<AlertEventView> getEvents(
      @AuthenticationPrincipal Jwt principal, @RequestParam(defaultValue = "50") int limit) {
    var userId = UUID.fromString(principal.getSubject());
    return eventRepository.findRecentByUserId(userId, limit).stream()
        .map(
            event ->
                new AlertEventView(
                    event.id(),
                    event.ticker(),
                    event.price(),
                    event.triggeredAt(),
                    event.readAt() != null))
        .toList();
  }

  /**
   * Marque un événement d'alerte comme lu (404 si l'événement n'appartient pas à l'utilisateur).
   */
  @PostMapping("/events/{id}/read")
  ResponseEntity<Void> markAsRead(@AuthenticationPrincipal Jwt principal, @PathVariable UUID id) {
    var userId = UUID.fromString(principal.getSubject());

    // Vérifie la propriété de l'événement avant de le marquer comme lu
    var event = eventRepository.findById(id);
    if (event.isEmpty() || !event.get().userId().equals(userId)) {
      return ResponseEntity.notFound().build();
    }

    eventRepository.markAsRead(id);
    return ResponseEntity.noContent().build();
  }
}
