package com.bloomfield.terminal.marketdata.internal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Client HTTP bas-niveau de l'API non officielle {@code /api/general/GetHistos} de Sikafinance.
 *
 * <p>Couche anti-corruption : toute la représentation amont (ticker {@code SGBCI}, date {@code
 * DD/MM/YYYY}, champ {@code lst}, prix entiers) meurt à l'intérieur de cette classe. Le reste du
 * code reçoit des {@link HistoricalBar} portant le code BRVM canonique, des {@link BigDecimal}
 * scalés à 2 décimales et des {@link Instant} positionnés à la clôture de séance 15:30 UTC
 * (Africa/Abidjan, UTC+0 sans DST).
 */
public final class SikafinanceClient {

  private static final Logger log = LoggerFactory.getLogger(SikafinanceClient.class);

  /**
   * Table de correspondance ticker canonique BRVM → ticker tel qu'utilisé par Sikafinance. Enrichir
   * au fil des 404 observés. Une entrée absente signifie « même code des deux côtés ».
   */
  static final Map<String, String> BRVM_TO_SIKAFINANCE = Map.of("SGBC", "SGBCI");

  private static final DateTimeFormatter RESPONSE_DATE_FMT =
      DateTimeFormatter.ofPattern("dd/MM/yyyy");
  private static final DateTimeFormatter REQUEST_DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;
  private static final LocalTime SESSION_CLOSE = LocalTime.of(15, 30);
  private static final long RETRY_BACKOFF_MS = 1000L;

  private final RestClient rest;

  SikafinanceClient(RestClient rest) {
    this.rest = rest;
  }

  /**
   * Rapatrie les bougies journalières sur l'intervalle {@code [from, to]} pour le ticker BRVM
   * demandé. Renvoie une liste vide si l'upstream répond avec {@code lst} vide ou absent.
   */
  List<HistoricalBar> fetchDaily(String brvmTicker, LocalDate from, LocalDate to) {
    String upstreamTicker = BRVM_TO_SIKAFINANCE.getOrDefault(brvmTicker, brvmTicker);
    var body =
        Map.<String, Object>of(
            "ticker",
            upstreamTicker,
            "datedeb",
            from.format(REQUEST_DATE_FMT),
            "datefin",
            to.format(REQUEST_DATE_FMT),
            "xperiod",
            "0");

    ResponseBody response = postWithRetry(upstreamTicker, body);
    if (response == null || response.lst() == null || response.lst().isEmpty()) {
      log.debug("Sikafinance returned no rows for ticker={} from={} to={}", brvmTicker, from, to);
      return List.of();
    }
    return response.lst().stream().map(dto -> toBar(brvmTicker, dto)).toList();
  }

  private ResponseBody postWithRetry(String upstreamTicker, Map<String, Object> body) {
    try {
      return sendOnce(upstreamTicker, body);
    } catch (HttpServerErrorException | ResourceAccessException firstFailure) {
      log.warn(
          "Sikafinance upstream failure ({}) on ticker={}, retrying once after {}ms",
          firstFailure.getClass().getSimpleName(),
          upstreamTicker,
          RETRY_BACKOFF_MS);
      try {
        Thread.sleep(RETRY_BACKOFF_MS);
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(
            "Interrupted while backing off before Sikafinance retry", ie);
      }
      return sendOnce(upstreamTicker, body);
    }
    // Les 4xx (HttpClientErrorException) ne sont pas rattrapés : mauvais ticker ou blocage ToS,
    // inutile de réessayer.
  }

  private ResponseBody sendOnce(String upstreamTicker, Map<String, Object> body) {
    return rest.post()
        .uri("/api/general/GetHistos")
        .header("Referer", "https://www.sikafinance.com/marches/historiques/" + upstreamTicker)
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .body(ResponseBody.class);
  }

  private static HistoricalBar toBar(String brvmTicker, SikafinanceBarDto dto) {
    LocalDate date = LocalDate.parse(dto.date(), RESPONSE_DATE_FMT);
    // Clôture BRVM 15:30 à Abidjan (UTC+0, pas de DST) => l'Instant est directement en UTC.
    Instant bucket = date.atTime(SESSION_CLOSE).toInstant(ZoneOffset.UTC);
    return new HistoricalBar(
        brvmTicker,
        bucket,
        scale(dto.open()),
        scale(dto.high()),
        scale(dto.low()),
        scale(dto.close()),
        dto.volume());
  }

  private static BigDecimal scale(BigDecimal value) {
    // La colonne ohlcv.* est NUMERIC(20,4) ; on fixe 2 décimales (prix entiers côté Sikafinance).
    return value.setScale(2, RoundingMode.HALF_UP);
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  record ResponseBody(@JsonProperty("lst") List<SikafinanceBarDto> lst) {}

  /** Ligne {@code lst[i]} : prix entiers, date {@code DD/MM/YYYY}. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  record SikafinanceBarDto(
      @JsonProperty("Date") String date,
      @JsonProperty("Open") BigDecimal open,
      @JsonProperty("High") BigDecimal high,
      @JsonProperty("Low") BigDecimal low,
      @JsonProperty("Close") BigDecimal close,
      @JsonProperty("Volume") long volume) {}
}
