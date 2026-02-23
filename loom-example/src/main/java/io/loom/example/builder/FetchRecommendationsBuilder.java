package io.loom.example.builder;

import io.loom.core.builder.BuilderContext;
import io.loom.core.builder.LoomBuilder;
import io.loom.example.dto.Recommendation;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class FetchRecommendationsBuilder implements LoomBuilder<List<Recommendation>> {

    @Override
    public List<Recommendation> build(BuilderContext context) {
        String id = context.getPathVariable("id");
        // In a real app: context.upstream("recommendation-service").get(...)
        return List.of(
                new Recommendation("101", "Super Widget", "Customers also bought"),
                new Recommendation("202", "Widget Accessory Kit", "Frequently bought together")
        );
    }
}
