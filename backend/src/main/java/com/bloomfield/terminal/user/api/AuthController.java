package com.bloomfield.terminal.user.api;

import com.bloomfield.terminal.user.api.dto.LoginRequest;
import com.bloomfield.terminal.user.api.dto.LogoutRequest;
import com.bloomfield.terminal.user.api.dto.MeResponse;
import com.bloomfield.terminal.user.api.dto.RefreshRequest;
import com.bloomfield.terminal.user.api.dto.RegisterRequest;
import com.bloomfield.terminal.user.api.dto.TokenResponse;
import com.bloomfield.terminal.user.domain.UserAccount;
import com.bloomfield.terminal.user.internal.RoleMapper;
import com.bloomfield.terminal.user.internal.TokenPair;
import com.bloomfield.terminal.user.internal.UserService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
record AuthController(UserService userService) {

  @PostMapping("/register")
  ResponseEntity<Void> register(@Valid @RequestBody RegisterRequest request) {
    userService.register(request.email(), request.password(), request.fullName());
    return ResponseEntity.status(HttpStatus.CREATED).build();
  }

  @PostMapping("/login")
  TokenResponse login(@Valid @RequestBody LoginRequest request) {
    TokenPair pair = userService.login(request.email(), request.password());
    return new TokenResponse(pair.accessToken(), pair.refreshToken(), pair.expiresInSeconds());
  }

  @PostMapping("/refresh")
  TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
    TokenPair pair = userService.refresh(request.refreshToken());
    return new TokenResponse(pair.accessToken(), pair.refreshToken(), pair.expiresInSeconds());
  }

  @PostMapping("/logout")
  ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
    userService.logout(request.refreshToken());
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/me")
  MeResponse me(@AuthenticationPrincipal Jwt principal) {
    UUID userId = UUID.fromString(principal.getSubject());
    UserAccount user = userService.me(userId);
    return new MeResponse(
        user.id(), user.email(), user.fullName(), RoleMapper.fromRefs(user.roles()));
  }
}
