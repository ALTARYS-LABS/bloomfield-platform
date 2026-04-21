package com.bloomfield.terminal.shared;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuration CORS. Deux variantes selon le profil actif :
 *
 * <ul>
 *   <li>Profil {@code prod} : origines strictes (pas de wildcard). On fail-fast à la création du
 *       bean si une entrée contient {@code *}, car cookies + {@code allowCredentials=true} sont
 *       incompatibles avec les patterns wildcard en prod.
 *   <li>Autres profils (dev / staging / demo / test) : on tolère les patterns ({@code
 *       allowedOriginPatterns}) pour les previews Coolify et les domaines locaux.
 * </ul>
 */
@Configuration
class CorsConfig {

  private final CorsProperties corsProperties;

  CorsConfig(CorsProperties corsProperties) {
    this.corsProperties = corsProperties;
  }

  @Bean
  @Profile("prod")
  WebMvcConfigurer strictCorsConfigurer() {
    // Garde-fou : refuser le démarrage si quelqu'un ajoute un "*" par inadvertance en prod.
    for (String origin : corsProperties.allowedOrigins()) {
      if (origin.contains("*")) {
        throw new IllegalStateException(
            "CORS wildcard origin forbidden in prod profile: " + origin);
      }
    }
    return new WebMvcConfigurer() {
      @Override
      public void addCorsMappings(CorsRegistry registry) {
        registry
            .addMapping("/**")
            .allowedOrigins(corsProperties.allowedOrigins().toArray(String[]::new))
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .allowCredentials(true);
      }
    };
  }

  @Bean
  @Profile("!prod")
  WebMvcConfigurer permissiveCorsConfigurer() {
    return new WebMvcConfigurer() {
      @Override
      public void addCorsMappings(CorsRegistry registry) {
        registry
            .addMapping("/**")
            .allowedOriginPatterns(corsProperties.allowedOrigins().toArray(String[]::new))
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .allowCredentials(true);
      }
    };
  }
}
