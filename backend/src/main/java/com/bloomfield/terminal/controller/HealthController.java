package com.bloomfield.terminal.controller;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
record HealthController() {

  @GetMapping("/api/health")
  Map<String, String> health() {
    return Map.of("status", "OK", "service", "Bloomfield Terminal API");
  }
}
