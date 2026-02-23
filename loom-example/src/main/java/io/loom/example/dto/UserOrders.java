package io.loom.example.dto;

import java.math.BigDecimal;
import java.util.List;

public record UserOrders(List<Order> orders) {
    public record Order(String orderId, String productName, BigDecimal amount, String status) {}
}
