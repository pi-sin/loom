package io.loom.example.builder;

import io.loom.core.builder.BuilderContext;
import io.loom.core.builder.LoomBuilder;
import io.loom.example.dto.Review;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class FetchReviewsBuilder implements LoomBuilder<List<Review>> {

    @Override
    public List<Review> build(BuilderContext context) {
        String id = context.getPathVariable("id");
        // In a real app: return context.service("review-service").get("/reviews?productId=" + id, ...);
        return List.of(
                new Review("Alice", 5, "Excellent product!"),
                new Review("Bob", 4, "Good quality, fast delivery"),
                new Review("Charlie", 3, "Decent but overpriced")
        );
    }
}
