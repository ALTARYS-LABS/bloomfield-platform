package com.bloomfield.terminal.user.internal;

public class AccountDisabledException extends RuntimeException {

  public AccountDisabledException(String message) {
    super(message);
  }
}
