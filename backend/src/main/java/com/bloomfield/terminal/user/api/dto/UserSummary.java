package com.bloomfield.terminal.user.api.dto;

import com.bloomfield.terminal.user.api.Role;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

public record UserSummary(
    UUID id,
    String email,
    String fullName,
    boolean enabled,
    OffsetDateTime createdAt,
    Set<Role> roles) {}
