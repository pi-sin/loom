package io.loom.example.api;

import io.loom.core.annotation.LoomApi;
import io.loom.core.annotation.LoomGraph;
import io.loom.core.annotation.LoomHeaderParam;
import io.loom.core.annotation.LoomQueryParam;
import io.loom.core.annotation.Node;
import io.loom.example.builder.*;
import io.loom.example.dto.ProductDetailResponse;
import org.springframework.stereotype.Component;

@Component
@LoomApi(
    method = "GET",
    path = "/api/products/{id}",
    response = ProductDetailResponse.class,
    summary = "Get product details",
    description = "Fetches product info, pricing, reviews and recommendations in parallel using a DAG-based scatter-gather flow",
    tags = {"Products"},
    queryParams = {
        @LoomQueryParam(name = "currency", description = "Price currency (e.g. USD, EUR)"),
        @LoomQueryParam(name = "fields", description = "Comma-separated fields to include in the response")
    },
    headers = {
        @LoomHeaderParam(name = "X-API-Key", required = true, description = "API authentication key")
    },
    guards = {"apiKeyGuard"}
)
@LoomGraph({
    @Node(builder = FetchProductBuilder.class, timeoutMs = 3000),
    @Node(builder = FetchPricingBuilder.class, dependsOn = FetchProductBuilder.class, timeoutMs = 3000),
    @Node(builder = FetchReviewsBuilder.class, required = false, timeoutMs = 2000),
    @Node(builder = FetchRecommendationsBuilder.class, required = false, timeoutMs = 2000),
    @Node(builder = AssembleProductBuilder.class,
          dependsOn = {FetchProductBuilder.class, FetchPricingBuilder.class,
                       FetchReviewsBuilder.class, FetchRecommendationsBuilder.class})
})
public class ProductDetailApi {
}
