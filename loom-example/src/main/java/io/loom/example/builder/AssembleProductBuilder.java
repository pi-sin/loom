package io.loom.example.builder;

import io.loom.core.builder.BuilderContext;
import io.loom.core.builder.LoomBuilder;
import io.loom.example.dto.*;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AssembleProductBuilder implements LoomBuilder<ProductDetailResponse> {

    @Override
    public ProductDetailResponse build(BuilderContext context) {
        ProductInfo product = context.getDependency(ProductInfo.class);
        PricingInfo pricing = context.getDependency(PricingInfo.class);
        List<Review> reviews = context.getOptionalResultOf(FetchReviewsBuilder.class).orElse(List.of());
        List<Recommendation> recommendations = context.getOptionalResultOf(FetchRecommendationsBuilder.class).orElse(List.of());

        return new ProductDetailResponse(product, pricing, reviews, recommendations);
    }
}
