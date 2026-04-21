package com.bloomfield.terminal.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.bloomfield.terminal.user.api.dto.LoginRequest;
import com.bloomfield.terminal.user.api.dto.RegisterRequest;
import com.bloomfield.terminal.user.api.dto.TokenResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

class AdminControllerIT extends AbstractPostgresIT {

  @Autowired TestRestTemplate rest;
  @Autowired JdbcTemplate jdbc;

  @Test
  void viewerGets403_AdminGets200() {
    String viewerEmail = "viewer-" + UUID.randomUUID() + "@example.com";
    String adminEmail = "admin-" + UUID.randomUUID() + "@example.com";
    String password = "password123";

    rest.postForEntity(
        "/auth/register", new RegisterRequest(viewerEmail, password, "Viewer"), Void.class);
    rest.postForEntity(
        "/auth/register", new RegisterRequest(adminEmail, password, "Admin"), Void.class);

    // Promote admin user directly in DB: remove VIEWER (3) and add ADMIN (1).
    UUID adminId =
        jdbc.queryForObject("SELECT id FROM users WHERE email = ?", UUID.class, adminEmail);
    jdbc.update("DELETE FROM user_roles WHERE user_id = ?", adminId);
    jdbc.update("INSERT INTO user_roles (user_id, role_id) VALUES (?, 1)", adminId);

    String viewerToken = loginToken(viewerEmail, password);
    String adminToken = loginToken(adminEmail, password);

    HttpHeaders viewerHeaders = bearer(viewerToken);
    ResponseEntity<String> viewerResp =
        rest.exchange(
            "/admin/users", HttpMethod.GET, new HttpEntity<>(viewerHeaders), String.class);
    assertThat(viewerResp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

    HttpHeaders adminHeaders = bearer(adminToken);
    ResponseEntity<String> adminResp =
        rest.exchange("/admin/users", HttpMethod.GET, new HttpEntity<>(adminHeaders), String.class);
    assertThat(adminResp.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  private String loginToken(String email, String password) {
    ResponseEntity<TokenResponse> resp =
        rest.postForEntity("/auth/login", new LoginRequest(email, password), TokenResponse.class);
    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    return resp.getBody().accessToken();
  }

  private HttpHeaders bearer(String token) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    return headers;
  }
}
