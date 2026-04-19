package com.bloomfield.terminal.user.api;

import java.util.Optional;

/**
 * Point d'accès public du module {@code user} pour les autres modules qui ont besoin de résoudre un
 * email vers un identifiant utilisateur (par exemple les seeders de démo des modules portfolio et
 * alerts). On passe par une interface plutôt qu'un événement car le besoin est synchrone et
 * ponctuel au démarrage.
 */
public interface UserDirectory {

  /** Retourne la vue d'un utilisateur par email, ou {@code Optional.empty()} s'il n'existe pas. */
  Optional<UserView> findByEmail(String email);
}
