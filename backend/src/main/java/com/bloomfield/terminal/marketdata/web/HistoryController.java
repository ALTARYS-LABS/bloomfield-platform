package com.bloomfield.terminal.marketdata.web;

import com.bloomfield.terminal.marketdata.api.MarketDataProvider;
import com.bloomfield.terminal.marketdata.api.TickerState;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Métadonnées d'émetteur (secteur, capitalisation, PER, rendement). L'ancien endpoint {@code
 * /api/brvm/history/{ticker}} a été supprimé au profit de {@link CandleController} (STORY-008).
 */
@RestController
@RequestMapping("/api/brvm")
record HistoryController(MarketDataProvider provider) {

  @GetMapping("/emitters/{ticker}")
  Map<String, Object> getEmitterInfo(@PathVariable String ticker) {
    String upper = ticker.toUpperCase();
    return provider
        .tickerState(upper)
        .map(state -> emitterPayload(upper, state))
        .orElseGet(() -> Map.of("error", "Ticker not found"));
  }

  private static Map<String, Object> emitterPayload(String ticker, TickerState state) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("ticker", ticker);
    payload.put("name", state.name());
    payload.put("sector", state.sector());
    payload.put("type", state.type());
    payload.put("marketCap", state.marketCap());
    payload.put("per", state.per());
    payload.put("dividendYield", state.dividendYield());
    return payload;
  }
}
