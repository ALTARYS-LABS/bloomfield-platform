package com.bloomfield.terminal.marketdata.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Paramètres du seeder d'historique simulé. L'activation ({@code seedHistoryDays=true}) est gérée
 * par {@link org.springframework.boot.autoconfigure.condition.ConditionalOnProperty} sur le bean
 * {@link SimulatedHistorySeeder}; ce record porte uniquement les paramètres de génération.
 *
 * <p>Valeurs par défaut pensées pour la démo : 30 jours ouvrés de bougies journalières, amplitude
 * intra-day 2 % (plage [-1 %, +1 %] par tick de prix) pour un résultat visuellement crédible sans
 * effondrement du prix sur 30 jours.
 */
@ConfigurationProperties(prefix = "app.marketdata.simulated")
record SimulatedHistoryProperties(boolean seedHistoryDays, int seedDays, double dailyVolatility) {

  SimulatedHistoryProperties {
    if (seedDays <= 0) {
      seedDays = 30;
    }
    if (dailyVolatility <= 0) {
      dailyVolatility = 0.01;
    }
  }
}
