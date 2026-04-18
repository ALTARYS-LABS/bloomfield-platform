package com.bloomfield.terminal.marketdata.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum SecurityType {
  EQUITY,
  BOND;

  @JsonValue
  public String toJson() {
    return name().toLowerCase();
  }

  @JsonCreator
  public static SecurityType fromJson(String value) {
    return SecurityType.valueOf(value.toUpperCase());
  }
}
