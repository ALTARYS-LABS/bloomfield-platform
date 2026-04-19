package com.bloomfield.terminal.user.api.dto;

import com.bloomfield.terminal.user.api.Role;
import jakarta.validation.constraints.NotEmpty;
import java.util.Set;

public record UpdateRolesRequest(@NotEmpty Set<Role> roles) {}
