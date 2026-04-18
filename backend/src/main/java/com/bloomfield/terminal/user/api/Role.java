package com.bloomfield.terminal.user.api;

public enum Role {
  ADMIN((short) 1),
  ANALYST((short) 2),
  VIEWER((short) 3);

  private final short id;

  Role(short id) {
    this.id = id;
  }

  public short id() {
    return id;
  }

  public static Role fromId(short id) {
    for (Role role : values()) {
      if (role.id == id) {
        return role;
      }
    }
    throw new IllegalArgumentException("Unknown role id: " + id);
  }
}
