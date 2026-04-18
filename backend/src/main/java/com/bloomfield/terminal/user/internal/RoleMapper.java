package com.bloomfield.terminal.user.internal;

import com.bloomfield.terminal.user.api.Role;
import com.bloomfield.terminal.user.domain.UserRoleRef;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

public final class RoleMapper {

  private RoleMapper() {}

  public static Set<UserRoleRef> toRefs(Set<Role> roles) {
    return roles.stream()
        .map(r -> new UserRoleRef(r.id()))
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  public static Set<Role> fromRefs(Set<UserRoleRef> refs) {
    if (refs == null) {
      return Set.of();
    }
    return refs.stream()
        .map(r -> Role.fromId(r.roleId()))
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }
}
