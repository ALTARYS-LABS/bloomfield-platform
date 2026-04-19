package com.bloomfield.terminal.alerts.internal;

import static org.mockito.Mockito.*;

import com.bloomfield.terminal.alerts.api.dto.AlertEventView;
import com.bloomfield.terminal.alerts.domain.AlertEvent;
import com.bloomfield.terminal.alerts.domain.AlertRule;
import com.bloomfield.terminal.alerts.domain.ThresholdOperator;
import com.bloomfield.terminal.marketdata.api.QuoteTick;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests unitaires du moteur d'alertes. Vérifie le comportement des opérateurs (ABOVE, BELOW,
 * CROSSES_UP, CROSSES_DOWN) et le persistance des événements.
 */
@ExtendWith(MockitoExtension.class)
class AlertEngineTest {

  @Mock private AlertRuleRepository ruleRepository;
  @Mock private AlertEventRepository eventRepository;
  @Mock private AlertNotifier notifier;

  private PreviousPriceCache priceCache;
  private AlertEngine engine;

  private UUID userId;
  private UUID ruleId;
  private String ticker;
  private BigDecimal threshold;

  @BeforeEach
  void setUp() {
    userId = UUID.randomUUID();
    ruleId = UUID.randomUUID();
    ticker = "SNTS";
    threshold = BigDecimal.valueOf(20000);
    priceCache = new PreviousPriceCache();
    engine = new AlertEngine(ruleRepository, eventRepository, notifier, priceCache);
  }

  @Test
  void testABOVE_fires_when_price_exceeds_threshold() {
    /* Arrange */
    var rule =
        new AlertRule(
            ruleId, userId, ticker, ThresholdOperator.ABOVE, threshold, true, Instant.now());
    when(ruleRepository.findEnabledByTicker(ticker)).thenReturn(List.of(rule));

    var tick = new QuoteTick(ticker, BigDecimal.valueOf(20100), Instant.now());

    /* Act */
    engine.onQuoteTick(tick);

    /* Assert */
    /* Vérifie que l'événement a été persisté */
    verify(eventRepository).save(any(AlertEvent.class));
    /* Vérifie que la règle a été désactivée */
    verify(ruleRepository).save(argThat(r -> !r.enabled() && r.id().equals(ruleId)));
    /* Vérifie que la notification a été envoyée */
    verify(notifier).sendAlertToUser(eq(userId), any(AlertEventView.class));
  }

  @Test
  void testBELOW_fires_when_price_below_threshold() {
    /* Arrange */
    var rule =
        new AlertRule(
            ruleId, userId, ticker, ThresholdOperator.BELOW, threshold, true, Instant.now());
    when(ruleRepository.findEnabledByTicker(ticker)).thenReturn(List.of(rule));

    var tick = new QuoteTick(ticker, BigDecimal.valueOf(19900), Instant.now());

    /* Act */
    engine.onQuoteTick(tick);

    /* Assert */
    verify(eventRepository).save(any(AlertEvent.class));
    verify(ruleRepository).save(argThat(r -> !r.enabled()));
    verify(notifier).sendAlertToUser(eq(userId), any(AlertEventView.class));
  }

  @Test
  void testCROSSES_UP_fires_when_crossing_threshold_upward() {
    /* Arrange */
    var rule =
        new AlertRule(
            ruleId, userId, ticker, ThresholdOperator.CROSSES_UP, threshold, true, Instant.now());
    when(ruleRepository.findEnabledByTicker(ticker)).thenReturn(List.of(rule));

    /* Initialise le cache avec un prix inférieur au seuil */
    priceCache.updateAndGetPrevious(ticker, BigDecimal.valueOf(19900));

    var tick = new QuoteTick(ticker, BigDecimal.valueOf(20100), Instant.now());

    /* Act */
    engine.onQuoteTick(tick);

    /* Assert */
    verify(eventRepository).save(any(AlertEvent.class));
    verify(ruleRepository).save(argThat(r -> !r.enabled()));
    verify(notifier).sendAlertToUser(eq(userId), any(AlertEventView.class));
  }

  @Test
  void testCROSSES_UP_does_not_fire_without_previous_price() {
    /* Arrange */
    var rule =
        new AlertRule(
            ruleId, userId, ticker, ThresholdOperator.CROSSES_UP, threshold, true, Instant.now());
    when(ruleRepository.findEnabledByTicker(ticker)).thenReturn(List.of(rule));

    var tick = new QuoteTick(ticker, BigDecimal.valueOf(20100), Instant.now());

    /* Act */
    engine.onQuoteTick(tick);

    /* Assert */
    verify(eventRepository, never()).save(any());
    verify(notifier, never()).sendAlertToUser(any(), any());
  }

  @Test
  void testCROSSES_DOWN_fires_when_crossing_threshold_downward() {
    /* Arrange */
    var rule =
        new AlertRule(
            ruleId, userId, ticker, ThresholdOperator.CROSSES_DOWN, threshold, true, Instant.now());
    when(ruleRepository.findEnabledByTicker(ticker)).thenReturn(List.of(rule));

    /* Initialise le cache avec un prix supérieur au seuil */
    priceCache.updateAndGetPrevious(ticker, BigDecimal.valueOf(20100));

    var tick = new QuoteTick(ticker, BigDecimal.valueOf(19900), Instant.now());

    /* Act */
    engine.onQuoteTick(tick);

    /* Assert */
    verify(eventRepository).save(any(AlertEvent.class));
    verify(ruleRepository).save(argThat(r -> !r.enabled()));
    verify(notifier).sendAlertToUser(eq(userId), any(AlertEventView.class));
  }
}
