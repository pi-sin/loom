package io.loom.example.dto;

import java.math.BigDecimal;

public record PricingInfo(BigDecimal price, String currency, BigDecimal discount) {}
