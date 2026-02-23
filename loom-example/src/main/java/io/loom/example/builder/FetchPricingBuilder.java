package io.loom.example.builder;

import io.loom.core.builder.BuilderContext;
import io.loom.core.builder.LoomBuilder;
import io.loom.example.dto.PricingInfo;
import io.loom.example.dto.ProductInfo;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class FetchPricingBuilder implements LoomBuilder<PricingInfo> {

    @Override
    public PricingInfo build(BuilderContext context) {
        ProductInfo product = context.getDependency(ProductInfo.class);
        // In a real app: return context.upstream("pricing-service").get("/pricing/" + product.id(), PricingInfo.class);
        return new PricingInfo(new BigDecimal("49.99"), "USD", new BigDecimal("5.00"));
    }
}
