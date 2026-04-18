package com.bloomfield.terminal.user.web;

import com.bloomfield.terminal.user.internal.AccountDisabledException;
import com.bloomfield.terminal.user.internal.EmailAlreadyUsedException;
import com.bloomfield.terminal.user.internal.InvalidCredentialsException;
import com.bloomfield.terminal.user.internal.UserNotFoundException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {AuthController.class, UserAdminController.class})
class AuthExceptionHandler {

  @ExceptionHandler(EmailAlreadyUsedException.class)
  ResponseEntity<Map<String, String>> handleEmailAlreadyUsed(EmailAlreadyUsedException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
  }

  @ExceptionHandler(InvalidCredentialsException.class)
  ResponseEntity<Map<String, String>> handleInvalidCredentials(InvalidCredentialsException ex) {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", ex.getMessage()));
  }

  @ExceptionHandler(AccountDisabledException.class)
  ResponseEntity<Map<String, String>> handleAccountDisabled(AccountDisabledException ex) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", ex.getMessage()));
  }

  @ExceptionHandler(UserNotFoundException.class)
  ResponseEntity<Map<String, String>> handleUserNotFound(UserNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
  }
}
