package com.bloomfield.terminal.demo;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base des tests d'intégration qui couvrent les seeders {@code @Profile("demo")}. Force le profil
 * actif à {@code demo} pour que les trois seeders (user, portfolio, alert) s'exécutent au démarrage
 * du contexte Spring.
 */
@SpringBootTest
@ActiveProfiles("demo")
public abstract class AbstractDemoProfileIT {

  static final PostgreSQLContainer<?> POSTGRES;

  static {
    POSTGRES =
        new PostgreSQLContainer<>(
            DockerImageName.parse("timescale/timescaledb:latest-pg17")
                .asCompatibleSubstituteFor("postgres"));
    POSTGRES.start();
  }

  @DynamicPropertySource
  static void datasourceProps(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add(
        "app.jwt.secret", () -> "test-secret-test-secret-test-secret-test-secret-0123456789");
  }
}
