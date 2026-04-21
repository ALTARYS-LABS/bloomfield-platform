package com.bloomfield.terminal.user.api;

import com.bloomfield.terminal.user.api.dto.UpdateEnabledRequest;
import com.bloomfield.terminal.user.api.dto.UpdateRolesRequest;
import com.bloomfield.terminal.user.api.dto.UserSummary;
import com.bloomfield.terminal.user.domain.UserAccount;
import com.bloomfield.terminal.user.internal.RoleMapper;
import com.bloomfield.terminal.user.internal.UserService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/users")
@PreAuthorize("hasRole('ADMIN')")
class UserAdminController {

  private final UserService userService;

  UserAdminController(UserService userService) {
    this.userService = userService;
  }

  @GetMapping
  List<UserSummary> list() {
    return userService.listUsers().stream().map(UserAdminController::toSummary).toList();
  }

  @PatchMapping("/{id}/roles")
  UserSummary updateRoles(@PathVariable UUID id, @Valid @RequestBody UpdateRolesRequest request) {
    return toSummary(userService.updateRoles(id, request.roles()));
  }

  @PatchMapping("/{id}/enabled")
  UserSummary setEnabled(@PathVariable UUID id, @RequestBody UpdateEnabledRequest request) {
    return toSummary(userService.setEnabled(id, request.enabled()));
  }

  private static UserSummary toSummary(UserAccount user) {
    return new UserSummary(
        user.id(),
        user.email(),
        user.fullName(),
        user.enabled(),
        user.createdAt(),
        RoleMapper.fromRefs(user.roles()));
  }
}
