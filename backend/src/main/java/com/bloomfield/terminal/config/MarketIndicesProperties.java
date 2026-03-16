package com.bloomfield.terminal.config;

import java.math.BigDecimal;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "market.indices")
public record MarketIndicesProperties(BigDecimal compositeBase, BigDecimal brvm10Base) {}
