package com.bloomfield.terminal.marketdata.internal;

import com.bloomfield.terminal.marketdata.api.SecurityType;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

/**
 * Loads the BRVM ticker seed file once at application startup. SnakeYAML ships transitively with
 * Spring Boot, so no additional dependency is required.
 */
@Component
class TickerSeedLoader {

  private final List<TickerSeed> seeds;

  @Autowired
  TickerSeedLoader(@Value("classpath:data/brvm-tickers.yml") Resource resource) {
    this.seeds = load(resource);
  }

  TickerSeedLoader(List<TickerSeed> seeds) {
    this.seeds = List.copyOf(seeds);
  }

  List<TickerSeed> seeds() {
    return seeds;
  }

  static List<TickerSeed> load(Resource resource) {
    try (InputStream in = resource.getInputStream()) {
      Map<String, Object> root = new Yaml().load(in);
      Objects.requireNonNull(root, "brvm-tickers.yml is empty");
      @SuppressWarnings("unchecked")
      List<Map<String, Object>> entries = (List<Map<String, Object>>) root.get("tickers");
      Objects.requireNonNull(entries, "brvm-tickers.yml missing 'tickers' root key");
      return entries.stream().map(TickerSeedLoader::toSeed).toList();
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read brvm-tickers.yml", e);
    }
  }

  private static TickerSeed toSeed(Map<String, Object> entry) {
    return new TickerSeed(
        requireString(entry, "ticker"),
        requireString(entry, "name"),
        requireString(entry, "sector"),
        SecurityType.valueOf(requireString(entry, "type").toUpperCase()),
        decimal(entry, "openPrice"),
        decimal(entry, "marketCap"),
        decimal(entry, "per"),
        decimal(entry, "dividendYield"));
  }

  private static String requireString(Map<String, Object> entry, String key) {
    Object value = entry.get(key);
    if (value == null) {
      throw new IllegalStateException(
          "brvm-tickers.yml entry " + entry.get("ticker") + " missing '" + key + "'");
    }
    return value.toString();
  }

  private static BigDecimal decimal(Map<String, Object> entry, String key) {
    Object value = entry.get(key);
    if (value == null) {
      throw new IllegalStateException(
          "brvm-tickers.yml entry " + entry.get("ticker") + " missing '" + key + "'");
    }
    return new BigDecimal(value.toString());
  }
}
