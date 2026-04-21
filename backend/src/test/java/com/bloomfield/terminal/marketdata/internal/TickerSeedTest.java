package com.bloomfield.terminal.marketdata.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.bloomfield.terminal.marketdata.api.SecurityType;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class TickerSeedTest {

  private static final Set<String> ALLOWED_SECTORS =
      Set.of(
          "Finance",
          "Agriculture",
          "Industrie",
          "Distribution",
          "Services Publics",
          "Telecoms",
          "Energie",
          "Transport",
          "Autres",
          "Obligations");

  @Test
  void yamlLoadsExactlyFortyFiveEntriesWithNoDuplicates() {
    List<TickerSeed> seeds = TickerSeedLoader.load(new ClassPathResource("data/brvm-tickers.yml"));

    assertThat(seeds).hasSize(45);

    Set<String> tickers = seeds.stream().map(TickerSeed::ticker).collect(Collectors.toSet());
    assertThat(tickers).as("no duplicate tickers").hasSize(45);
  }

  @Test
  void everySeedHasKnownSectorAndSecurityType() {
    List<TickerSeed> seeds = TickerSeedLoader.load(new ClassPathResource("data/brvm-tickers.yml"));

    for (TickerSeed seed : seeds) {
      assertThat(ALLOWED_SECTORS)
          .as("unknown sector '%s' on ticker %s", seed.sector(), seed.ticker())
          .contains(seed.sector());
      assertThat(seed.type())
          .as("type must be EQUITY or BOND on ticker %s", seed.ticker())
          .isIn(SecurityType.EQUITY, SecurityType.BOND);
    }
  }

  @Test
  void bondsAreClassifiedInObligationsSector() {
    List<TickerSeed> seeds = TickerSeedLoader.load(new ClassPathResource("data/brvm-tickers.yml"));

    List<TickerSeed> bonds = seeds.stream().filter(s -> s.type() == SecurityType.BOND).toList();

    assertThat(bonds).isNotEmpty();
    bonds.forEach(b -> assertThat(b.sector()).isEqualTo("Obligations"));
  }
}
