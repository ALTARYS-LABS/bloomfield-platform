package com.bloomfield.terminal.marketdata.internal;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Paramètres de l'adaptateur historique Sikafinance. Défauts pensés pour un usage démo : espacement
 * conservateur (150 ms), fenêtre maximale de 89 jours imposée par l'endpoint amont. Toutes ces
 * valeurs sont pilotées par {@code application.yml} (règle #3).
 */
@ConfigurationProperties(prefix = "app.marketdata.sikafinance")
record SikafinanceProperties(
    String baseUrl,
    String userAgent,
    long requestSpacingMs,
    int maxWindowDays,
    Duration connectTimeout,
    Duration readTimeout) {

  /** Valeurs par défaut utilisées lorsque {@code application.yml} ne fournit pas la clé. */
  SikafinanceProperties {
    if (baseUrl == null || baseUrl.isBlank()) {
      baseUrl = "https://www.sikafinance.com";
    }
    if (userAgent == null || userAgent.isBlank()) {
      userAgent =
          "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:144.0) Gecko/20100101 Firefox/144.0";
    }
    if (requestSpacingMs <= 0) {
      requestSpacingMs = 150L;
    }
    if (maxWindowDays <= 0) {
      maxWindowDays = 89;
    }
    if (connectTimeout == null) {
      connectTimeout = Duration.ofSeconds(5);
    }
    if (readTimeout == null) {
      readTimeout = Duration.ofSeconds(15);
    }
  }
}
