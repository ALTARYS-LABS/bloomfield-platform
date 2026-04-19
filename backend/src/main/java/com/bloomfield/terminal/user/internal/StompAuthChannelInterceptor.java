package com.bloomfield.terminal.user.internal;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

/**
 * Reads the {@code Authorization: Bearer &lt;access-token&gt;} header on STOMP {@code CONNECT}
 * frames, validates the JWT via the same decoder used for HTTP auth, and attaches a {@link
 * org.springframework.security.core.Authentication} to the session. This is what enables {@code
 * /user/queue/*} STOMP destinations to target the right principal.
 *
 * <p>If the header is missing or invalid, the CONNECT proceeds anonymously so that public
 * market-data topics ({@code /topic/*}) continue to work for unauthenticated clients.
 */
@Component
class StompAuthChannelInterceptor implements ChannelInterceptor {

  private final JwtDecoder jwtDecoder;

  StompAuthChannelInterceptor(JwtDecoder jwtDecoder) {
    this.jwtDecoder = jwtDecoder;
  }

  @Override
  public Message<?> preSend(Message<?> message, MessageChannel channel) {
    StompHeaderAccessor accessor =
        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
    if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
      return message;
    }

    String authorization = accessor.getFirstNativeHeader("Authorization");
    if (authorization == null || !authorization.startsWith("Bearer ")) {
      return message;
    }

    try {
      Jwt jwt = jwtDecoder.decode(authorization.substring("Bearer ".length()));
      var token = new UsernamePasswordAuthenticationToken(jwt.getSubject(), null, authorities(jwt));
      accessor.setUser(token);
    } catch (JwtException ex) {
      // Invalid token: fall through as anonymous; public topics still work.
    }
    return message;
  }

  private static Collection<GrantedAuthority> authorities(Jwt jwt) {
    List<String> roles = jwt.getClaimAsStringList("roles");
    if (roles == null) {
      return List.of();
    }
    return roles.stream()
        .map(r -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r))
        .collect(Collectors.toList());
  }
}
