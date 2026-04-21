package com.bloomfield.terminal.alerts;

import static org.assertj.core.api.Assertions.assertThat;

import com.bloomfield.terminal.alerts.api.dto.AlertRuleRequest;
import com.bloomfield.terminal.alerts.api.dto.AlertRuleView;
import com.bloomfield.terminal.alerts.domain.ThresholdOperator;
import com.bloomfield.terminal.user.AbstractPostgresIT;
import com.bloomfield.terminal.user.api.dto.LoginRequest;
import com.bloomfield.terminal.user.api.dto.RegisterRequest;
import com.bloomfield.terminal.user.api.dto.TokenResponse;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Tests d'intégration pour l'API des alertes. Vérifie l'isolation par utilisateur, la persistance
 * et l'accès aux ressources.
 */
class AlertControllerIT extends AbstractPostgresIT {

  @Autowired TestRestTemplate rest;

  @Test
  void createRuleAndListRules() {
    // Enregistre un utilisateur
    var email = "alert-user-" + UUID.randomUUID() + "@example.com";
    rest.postForEntity(
        "/auth/register", new RegisterRequest(email, "password123", "Alert User"), Void.class);

    // Connexion et récupération du token
    ResponseEntity<TokenResponse> login =
        rest.postForEntity(
            "/auth/login", new LoginRequest(email, "password123"), TokenResponse.class);
    assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
    var token = login.getBody().accessToken();

    // Crée une règle
    var headers = new HttpHeaders();
    headers.setBearerAuth(token);
    var request = new AlertRuleRequest("SNTS", ThresholdOperator.ABOVE, BigDecimal.valueOf(20000));
    ResponseEntity<AlertRuleView> createResp =
        rest.exchange(
            "/api/alerts/rules",
            HttpMethod.POST,
            new HttpEntity<>(request, headers),
            AlertRuleView.class);
    assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    var rule = createResp.getBody();
    assertThat(rule).isNotNull();
    assertThat(rule.ticker()).isEqualTo("SNTS");
    assertThat(rule.operator()).isEqualTo(ThresholdOperator.ABOVE);
    assertThat(rule.enabled()).isTrue();

    // Liste les règles
    ResponseEntity<AlertRuleView[]> listResp =
        rest.exchange(
            "/api/alerts/rules", HttpMethod.GET, new HttpEntity<>(headers), AlertRuleView[].class);
    assertThat(listResp.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(listResp.getBody()).isNotEmpty();
  }

  @Test
  void deleteRuleForeignReturn404() {
    // Enregistre deux utilisateurs
    var email1 = "user1-" + UUID.randomUUID() + "@example.com";
    var email2 = "user2-" + UUID.randomUUID() + "@example.com";
    rest.postForEntity(
        "/auth/register", new RegisterRequest(email1, "password123", "User 1"), Void.class);
    rest.postForEntity(
        "/auth/register", new RegisterRequest(email2, "password123", "User 2"), Void.class);

    // User 1 se connecte et crée une règle
    ResponseEntity<TokenResponse> login1 =
        rest.postForEntity(
            "/auth/login", new LoginRequest(email1, "password123"), TokenResponse.class);
    var token1 = login1.getBody().accessToken();
    var headers1 = new HttpHeaders();
    headers1.setBearerAuth(token1);

    var request = new AlertRuleRequest("SNTS", ThresholdOperator.ABOVE, BigDecimal.valueOf(20000));
    ResponseEntity<AlertRuleView> createResp =
        rest.exchange(
            "/api/alerts/rules",
            HttpMethod.POST,
            new HttpEntity<>(request, headers1),
            AlertRuleView.class);
    var ruleId = createResp.getBody().id();

    // User 2 se connecte et tente de supprimer la règle de User 1
    ResponseEntity<TokenResponse> login2 =
        rest.postForEntity(
            "/auth/login", new LoginRequest(email2, "password123"), TokenResponse.class);
    var token2 = login2.getBody().accessToken();
    var headers2 = new HttpHeaders();
    headers2.setBearerAuth(token2);

    ResponseEntity<Void> deleteResp =
        rest.exchange(
            "/api/alerts/rules/" + ruleId,
            HttpMethod.DELETE,
            new HttpEntity<>(headers2),
            Void.class);
    assertThat(deleteResp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

    // User 1 peut toujours supprimer sa propre règle
    ResponseEntity<Void> deleteOwnResp =
        rest.exchange(
            "/api/alerts/rules/" + ruleId,
            HttpMethod.DELETE,
            new HttpEntity<>(headers1),
            Void.class);
    assertThat(deleteOwnResp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
  }

  @Test
  void unauthedAccessReturns401() {
    ResponseEntity<String> resp = rest.getForEntity("/api/alerts/rules", String.class);
    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }
}
