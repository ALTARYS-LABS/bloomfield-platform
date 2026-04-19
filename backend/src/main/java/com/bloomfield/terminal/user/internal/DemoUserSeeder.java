package com.bloomfield.terminal.user.internal;

import com.bloomfield.terminal.user.api.Role;
import com.bloomfield.terminal.user.domain.UserAccount;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seed idempotent des trois comptes de démo (ADMIN, ANALYST, VIEWER). S'exécute uniquement sous le
 * profil {@code demo} et avant les seeders portfolio / alerts grâce à {@link Order}.
 *
 * <p>Les mots de passe sont surchargeables via variables d'environnement pour éviter de les figer
 * dans le code (cf. README section "Demo credentials"). Le hash BCrypt est calculé à chaque
 * démarrage sur le mot de passe courant : si un admin change la variable d'env, le seed détecte le
 * compte existant et ne retouche pas le hash (idempotence stricte).
 */
@Component
@Profile("demo")
@Order(1)
class DemoUserSeeder implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(DemoUserSeeder.class);

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final String adminPassword;
  private final String analystPassword;
  private final String viewerPassword;

  DemoUserSeeder(
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      @Value("${demo.users.admin-password:ChangeMe!Admin2026}") String adminPassword,
      @Value("${demo.users.analyst-password:ChangeMe!Analyst2026}") String analystPassword,
      @Value("${demo.users.viewer-password:ChangeMe!Viewer2026}") String viewerPassword) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.adminPassword = adminPassword;
    this.analystPassword = analystPassword;
    this.viewerPassword = viewerPassword;
  }

  @Override
  public void run(ApplicationArguments args) {
    seed("admin@altaryslabs.com", "Altarys Admin", adminPassword, Set.of(Role.ADMIN));
    seed("analyst@demo.bloomfield", "Demo Analyst", analystPassword, Set.of(Role.ANALYST));
    seed("viewer@demo.bloomfield", "Demo Viewer", viewerPassword, Set.of(Role.VIEWER));
  }

  private void seed(String email, String fullName, String password, Set<Role> roles) {
    // Idempotence : si l'email existe déjà, on ne touche à rien (ni au hash, ni aux rôles).
    if (userRepository.existsByEmail(email)) {
      log.debug("Demo user already present, skipping seed: {}", email);
      return;
    }
    var user =
        UserAccount.newUser(
            UUID.randomUUID(),
            email,
            passwordEncoder.encode(password),
            fullName,
            true,
            OffsetDateTime.now(ZoneOffset.UTC),
            RoleMapper.toRefs(roles));
    userRepository.save(user);
    log.info("Demo user seeded: {} with roles {}", email, roles);
  }
}
