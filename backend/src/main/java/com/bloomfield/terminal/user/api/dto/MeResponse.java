package com.bloomfield.terminal.user.api.dto;

import com.bloomfield.terminal.user.api.Role;
import java.util.Set;
import java.util.UUID;

public record MeResponse(UUID id, String email, String fullName, Set<Role> roles) {}
