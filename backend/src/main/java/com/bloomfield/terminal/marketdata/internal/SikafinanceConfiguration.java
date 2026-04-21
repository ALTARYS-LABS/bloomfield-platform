package com.bloomfield.terminal.marketdata.internal;

import java.net.http.HttpClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Wiring de l'adaptateur historique Sikafinance. L'intégralité des beans est gatée par {@code
 * app.marketdata.history-source=sikafinance} : en mode {@code simulated} (défaut), rien n'est
 * instancié et la CI reste hermétique (aucun trafic HTTP sortant).
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.marketdata.history-source", havingValue = "sikafinance")
class SikafinanceConfiguration {

  @Bean
  RestClient sikafinanceRestClient(SikafinanceProperties props) {
    // HttpClient JDK : connectTimeout géré au niveau client, readTimeout au niveau factory.
    // HTTP/1.1 forcé : évite une tentative d'upgrade h2c en clair face à WireMock (tests) et
    // reste compatible avec Sikafinance en HTTPS (pas de bénéfice HTTP/2 pour cette API simple).
    HttpClient httpClient =
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(props.connectTimeout())
            .build();
    var factory = new JdkClientHttpRequestFactory(httpClient);
    factory.setReadTimeout(props.readTimeout());
    return RestClient.builder()
        .baseUrl(props.baseUrl())
        .defaultHeader("User-Agent", props.userAgent())
        .defaultHeader("Origin", "https://www.sikafinance.com")
        .requestFactory(factory)
        .build();
  }

  @Bean
  SikafinanceClient sikafinanceClient(RestClient sikafinanceRestClient) {
    return new SikafinanceClient(sikafinanceRestClient);
  }

  @Bean
  HistoricalCandleLoader historicalCandleLoader(
      SikafinanceClient client, OhlcvRepository repository, SikafinanceProperties props) {
    return new HistoricalCandleLoader(client, repository, props);
  }
}
