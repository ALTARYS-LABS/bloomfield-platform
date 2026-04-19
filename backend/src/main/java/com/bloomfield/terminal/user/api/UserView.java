package com.bloomfield.terminal.user.api;

import java.util.Set;
import java.util.UUID;

/**
 * Vue en lecture seule d'un compte utilisateur exposée aux autres modules via {@link
 * UserDirectory}. On ne fait pas fuiter le hash de mot de passe ni les objets de domaine.
 */
public record UserView(UUID id, String email, String fullName, boolean enabled, Set<Role> roles) {}
