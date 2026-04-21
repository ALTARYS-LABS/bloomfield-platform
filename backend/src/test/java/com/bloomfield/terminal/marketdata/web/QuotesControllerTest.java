package com.bloomfield.terminal.marketdata.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bloomfield.terminal.marketdata.api.MarketDataProvider;
import com.bloomfield.terminal.marketdata.api.Quote;
import com.bloomfield.terminal.marketdata.api.SecurityType;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(QuotesController.class)
class QuotesControllerTest {

  @Autowired MockMvc mockMvc;

  @MockitoBean MarketDataProvider provider;

  private static Quote quote(String ticker, String sector, SecurityType type) {
    BigDecimal price = new BigDecimal("1000.00");
    return new Quote(
        ticker,
        ticker + " Co",
        sector,
        type,
        price,
        price,
        price,
        price,
        price,
        100L,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        0L);
  }

  @BeforeEach
  void seedProvider() {
    when(provider.currentQuotes())
        .thenReturn(
            List.of(
                quote("SGBC", "Finance", SecurityType.EQUITY),
                quote("ECOC", "Finance", SecurityType.EQUITY),
                quote("PALC", "Agriculture", SecurityType.EQUITY),
                quote("TPCI25", "Obligations", SecurityType.BOND)));
  }

  @Test
  void returnsAllQuotesWhenNoFilter() throws Exception {
    mockMvc
        .perform(get("/api/brvm/quotes"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(4));
  }

  @Test
  void filtersBySector() throws Exception {
    mockMvc
        .perform(get("/api/brvm/quotes").param("sector", "Finance"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].sector").value("Finance"))
        .andExpect(jsonPath("$[1].sector").value("Finance"));
  }

  @Test
  void filtersByType() throws Exception {
    mockMvc
        .perform(get("/api/brvm/quotes").param("type", "bond"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].ticker").value("TPCI25"))
        .andExpect(jsonPath("$[0].type").value("bond"));
  }

  @Test
  void combinesSectorAndTypeFilters() throws Exception {
    mockMvc
        .perform(get("/api/brvm/quotes").param("sector", "Finance").param("type", "equity"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2));

    mockMvc
        .perform(get("/api/brvm/quotes").param("sector", "Finance").param("type", "bond"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void unknownSectorReturnsEmptyList() throws Exception {
    mockMvc
        .perform(get("/api/brvm/quotes").param("sector", "DoesNotExist"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }
}
