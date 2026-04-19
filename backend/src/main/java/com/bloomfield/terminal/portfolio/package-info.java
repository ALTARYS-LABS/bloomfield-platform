// Module Portefeuille : consomme l'API publique de `marketdata` pour la valorisation et
// `user::api` (UserDirectory) uniquement pour le seeder de démo.
@org.springframework.modulith.ApplicationModule(
    displayName = "Portfolio",
    allowedDependencies = {"marketdata::api", "user::api"})
package com.bloomfield.terminal.portfolio;
