package io.loom.example.builder;

import io.loom.core.builder.BuilderContext;
import io.loom.core.builder.LoomBuilder;
import io.loom.example.dto.UserOrders;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class FetchUserOrdersBuilder implements LoomBuilder<UserOrders> {

    @Override
    public UserOrders build(BuilderContext context) {
        String userId = context.getPathVariable("userId");
        // In a real app: return context.service("order-service").route("get-user-orders")
        //   .queryParam("userId", userId).get(UserOrders.class);
        return new UserOrders(List.of(
                new UserOrders.Order("ORD-001", "Premium Widget", new BigDecimal("49.99"), "delivered"),
                new UserOrders.Order("ORD-002", "Widget Accessory Kit", new BigDecimal("19.99"), "shipped")
        ));
    }
}
