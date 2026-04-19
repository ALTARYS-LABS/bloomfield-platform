// Module Portefeuille : consomme uniquement l'API publique de `marketdata` pour la valorisation.
// L'authentification passe par Spring Security (Jwt principal), pas par un import du module user.
@org.springframework.modulith.ApplicationModule(
    displayName = "Portfolio",
    allowedDependencies = {"marketdata::api"})
package com.bloomfield.terminal.portfolio;
