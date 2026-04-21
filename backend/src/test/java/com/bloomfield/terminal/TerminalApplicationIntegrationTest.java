package com.bloomfield.terminal;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers
class TerminalApplicationIntegrationTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(
          DockerImageName.parse("timescale/timescaledb:latest-pg17")
              .asCompatibleSubstituteFor("postgres"));

  @org.springframework.test.context.DynamicPropertySource
  static void datasourceProps(org.springframework.test.context.DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add(
        "app.jwt.secret", () -> "test-secret-test-secret-test-secret-test-secret-0123456789");
  }

  @Test
  void contextLoads() {}
}
