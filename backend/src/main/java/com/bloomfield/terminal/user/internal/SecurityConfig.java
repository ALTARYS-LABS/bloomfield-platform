package com.bloomfield.terminal.user.internal;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties({JwtProperties.class, CookieProperties.class})
class SecurityConfig {

  private final JwtProperties jwtProperties;

  SecurityConfig(JwtProperties jwtProperties) {
    this.jwtProperties = jwtProperties;
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  JwtDecoder jwtDecoder() {
    SecretKeySpec key = new SecretKeySpec(jwtProperties.secret().getBytes(), "HmacSHA256");
    return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
  }

  @Bean
  JwtEncoder jwtEncoder() {
    SecretKeySpec key = new SecretKeySpec(jwtProperties.secret().getBytes(), "HmacSHA256");
    return new NimbusJwtEncoder(new ImmutableSecret<>(key));
  }

  @Bean
  JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
    authorities.setAuthoritiesClaimName("roles");
    authorities.setAuthorityPrefix("ROLE_");
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(authorities);
    converter.setPrincipalClaimName("sub");
    return converter;
  }

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http, JwtAuthenticationConverter jwtAuthenticationConverter) throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .cors(Customizer.withDefaults())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        "/api/health",
                        "/auth/register",
                        "/auth/login",
                        "/auth/refresh",
                        "/auth/logout",
                        "/ws/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(
            oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)));
    return http.build();
  }
}
