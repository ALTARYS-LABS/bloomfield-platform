package com.bloomfield.terminal.controller;

import java.util.List;
import java.util.Map;

import com.bloomfield.terminal.service.MarketDataSimulator;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/brvm")
record HistoryController(MarketDataSimulator simulator) {

    @GetMapping("/history/{ticker}")
    List<Map<String, Object>> getHistory(@PathVariable String ticker) {
        return simulator.generateHistory(ticker.toUpperCase(), 30);
    }

    @GetMapping("/emitters/{ticker}")
    Map<String, Object> getEmitterInfo(@PathVariable String ticker) {
        var state = simulator.getTickerState(ticker.toUpperCase());
        if (state == null) return Map.of("error", "Ticker not found");

        return Map.of(
                "ticker", ticker.toUpperCase(),
                "name", state.name(),
                "sector", state.sector(),
                "marketCap", state.marketCap(),
                "per", state.per(),
                "dividendYield", state.dividendYield()
        );
    }
}
