package com.bloomfield.terminal.alerts.internal;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Configuration du module d'alertes. */
@Configuration
class AlertsConfig {

  /** Cache des prix précédents pour évaluer les opérateurs CROSSES. */
  @Bean
  PreviousPriceCache previousPriceCache() {
    return new PreviousPriceCache();
  }
}
