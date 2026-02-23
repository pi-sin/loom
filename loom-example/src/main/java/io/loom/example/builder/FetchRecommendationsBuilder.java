package io.loom.example.builder;

import io.loom.core.builder.BuilderContext;
import io.loom.core.builder.LoomBuilder;
import io.loom.example.dto.PricingInfo;
import io.loom.example.dto.ProductInfo;
import io.loom.example.dto.Recommendation;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class FetchRecommendationsBuilder implements LoomBuilder<List<Recommendation>> {

    @Override
    public List<Recommendation> build(BuilderContext context) {
        ProductInfo product = context.getDependency(ProductInfo.class);
        PricingInfo pricing = context.getDependency(PricingInfo.class);
        // In a real app: context.service("recommendation-service")
        //   .get("/recommendations?category=" + product.category() + "&priceRange=" + pricing.price(), ...)
        return List.of(
                new Recommendation("101", "Super Widget", "Similar in " + product.category()),
                new Recommendation("202", "Widget Accessory Kit", "Frequently bought together")
        );
    }
}
