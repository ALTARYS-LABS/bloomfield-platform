package com.bloomfield.terminal.user.internal;

import com.bloomfield.terminal.user.api.UserDirectory;
import com.bloomfield.terminal.user.api.UserView;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Implémentation par défaut de {@link UserDirectory}. Passe par {@link UserRepository} et ne
 * retourne que les champs publics via {@link UserView}.
 */
@Component
class UserDirectoryImpl implements UserDirectory {

  private final UserRepository userRepository;

  UserDirectoryImpl(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @Override
  public Optional<UserView> findByEmail(String email) {
    return userRepository
        .findByEmail(email)
        .map(
            u ->
                new UserView(
                    u.id(), u.email(), u.fullName(), u.enabled(), RoleMapper.fromRefs(u.roles())));
  }
}
