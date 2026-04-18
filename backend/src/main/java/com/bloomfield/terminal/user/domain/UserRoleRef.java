package com.bloomfield.terminal.user.domain;

import org.springframework.data.relational.core.mapping.Table;

@Table("user_roles")
public record UserRoleRef(short roleId) {}
