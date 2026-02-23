package io.loom.example.dto;

import java.math.BigDecimal;
import java.util.List;

public record CreateOrderRequest(
    String customerId,
    List<OrderItem> items,
    String shippingAddress
) {
    public record OrderItem(
        String productId,
        int quantity,
        BigDecimal unitPrice
    ) {}
}
