package com.bloomfield.terminal;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class TerminalApplication {
  public static void main(String[] args) {
    SpringApplication.run(TerminalApplication.class, args);
  }

  // Horloge centralisée : injectée dans les services qui horodatent (portfolio, trades, alerts).
  // Permet de stubber le temps dans les tests sans toucher à System.now().
  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }
}
