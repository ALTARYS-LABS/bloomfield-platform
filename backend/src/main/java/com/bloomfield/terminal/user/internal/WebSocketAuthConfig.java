package com.bloomfield.terminal.user.internal;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Registers {@link StompAuthChannelInterceptor} on the inbound STOMP channel so {@code /user/*}
 * destinations resolve to an authenticated {@code Principal}. The marketdata module owns the broker
 * topology (broker prefixes, endpoint registration, SockJS); this configurer only adds auth.
 */
@Configuration
class WebSocketAuthConfig implements WebSocketMessageBrokerConfigurer {

  private final StompAuthChannelInterceptor stompAuthChannelInterceptor;

  WebSocketAuthConfig(StompAuthChannelInterceptor stompAuthChannelInterceptor) {
    this.stompAuthChannelInterceptor = stompAuthChannelInterceptor;
  }

  @Override
  public void configureClientInboundChannel(ChannelRegistration registration) {
    registration.interceptors(stompAuthChannelInterceptor);
  }
}
