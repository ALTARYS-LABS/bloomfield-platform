package com.bloomfield.terminal.marketdata.config;

import com.bloomfield.terminal.shared.CorsProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

  private final CorsProperties corsProperties;

  WebSocketConfig(CorsProperties corsProperties) {
    this.corsProperties = corsProperties;
  }

  @Override
  public void configureMessageBroker(MessageBrokerRegistry config) {
    // /topic : diffusions broadcast (market data). /queue : destinations user-spécifiques
    // (portefeuille).
    config.enableSimpleBroker("/topic", "/queue");
    config.setApplicationDestinationPrefixes("/app");
  }

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    registry
        .addEndpoint("/ws")
        .setAllowedOriginPatterns(corsProperties.allowedOrigins().toArray(String[]::new))
        .withSockJS();
  }
}
