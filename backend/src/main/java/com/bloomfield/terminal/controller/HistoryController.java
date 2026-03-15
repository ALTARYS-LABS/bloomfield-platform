package com.bloomfield.terminal.controller;

import com.bloomfield.terminal.service.MarketDataSimulator;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/brvm")
public class HistoryController {

    private final MarketDataSimulator simulator;

    public HistoryController(MarketDataSimulator simulator) {
        this.simulator = simulator;
    }

    @GetMapping("/history/{ticker}")
    public List<Map<String, Object>> getHistory(@PathVariable String ticker) {
        return simulator.generateHistory(ticker.toUpperCase(), 30);
    }

    @GetMapping("/emitters/{ticker}")
    public Map<String, Object> getEmitterInfo(@PathVariable String ticker) {
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
