package io.loom.example.dto;

import java.math.BigDecimal;

public record OrderResponse(
    String orderId,
    String status,
    BigDecimal totalAmount,
    String estimatedDelivery
) {}
