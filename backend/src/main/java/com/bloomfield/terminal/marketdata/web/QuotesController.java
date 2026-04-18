package com.bloomfield.terminal.marketdata.web;

import com.bloomfield.terminal.marketdata.api.MarketDataProvider;
import com.bloomfield.terminal.marketdata.api.Quote;
import com.bloomfield.terminal.marketdata.api.SecurityType;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/brvm")
record QuotesController(MarketDataProvider provider) {

  @GetMapping("/quotes")
  List<Quote> quotes(
      @RequestParam(required = false) String sector, @RequestParam(required = false) String type) {
    SecurityType typeFilter = parseType(type);
    if (type != null && typeFilter == null) {
      // Unknown type value: mirror the unknown-sector behavior and return an empty list.
      return List.of();
    }
    return provider.currentQuotes().stream()
        .filter(q -> sector == null || sector.equals(q.sector()))
        .filter(q -> typeFilter == null || typeFilter == q.type())
        .toList();
  }

  private static SecurityType parseType(String type) {
    if (type == null) return null;
    try {
      return SecurityType.fromJson(type);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }
}
