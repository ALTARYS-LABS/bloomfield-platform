package com.bloomfield.terminal.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit test du garde-fou CORS : en profil prod, la présence d'un caractère {@code *} dans {@code
 * app.cors.allowed-origins} doit provoquer l'échec immédiat de la création du bean.
 */
class CorsWildcardFailFastTest {

  @Test
  void wildcardOriginInProdThrows() {
    var config = new CorsConfig(new CorsProperties(List.of("https://app.example.com", "*")));
    assertThatThrownBy(config::strictCorsConfigurer)
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("CORS wildcard origin forbidden in prod profile: *");
  }

  @Test
  void exactOriginsInProdAreAccepted() {
    var config =
        new CorsConfig(
            new CorsProperties(
                List.of(
                    "https://bloomfield-intelligence.altaryslabs.com",
                    "https://staging-bf-terminal.altaryslabs.com")));
    assertThat(config.strictCorsConfigurer()).isNotNull();
  }
}
