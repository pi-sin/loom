package io.loom.example.builder;

import io.loom.core.builder.BuilderContext;
import io.loom.core.builder.LoomBuilder;
import io.loom.example.dto.PricingInfo;
import io.loom.example.dto.ProductInfo;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class FetchPricingBuilder implements LoomBuilder<PricingInfo> {

    @Override
    public PricingInfo build(BuilderContext context) {
        ProductInfo product = context.getDependency(ProductInfo.class);
        String currency = context.getQueryParam("currency");

        if (StringUtils.isEmpty(currency)) {
            currency = "USD";
        }

        // In a real app: return context.service("pricing-service").route("get-pricing")
        //   .pathVar("id", product.id()).get(PricingInfo.class);
        return new PricingInfo(new BigDecimal("49.99"), currency, new BigDecimal("5.00"));
    }
}
