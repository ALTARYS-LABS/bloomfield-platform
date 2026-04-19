package com.bloomfield.terminal.portfolio.internal;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// Mappe les erreurs métier du module portefeuille en HTTP 400 : ce sont toutes des requêtes
// invalides côté client (ticker inconnu, quantité insuffisante). Le message reste exposé tel
// quel car il ne fuit pas de secret et guide l'utilisateur.
// Placé dans internal/ pour garder les exceptions package-private ; ciblé explicitement sur
// PortfolioController pour ne pas intercepter les erreurs d'autres modules.
@RestControllerAdvice(basePackages = "com.bloomfield.terminal.portfolio.api")
class PortfolioExceptionHandler {

  @ExceptionHandler(UnknownTickerException.class)
  ResponseEntity<Map<String, String>> handleUnknownTicker(UnknownTickerException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
  }

  @ExceptionHandler(InsufficientPositionException.class)
  ResponseEntity<Map<String, String>> handleInsufficientPosition(InsufficientPositionException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
  }
}
