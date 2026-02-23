package io.loom.example.dto;

import java.util.List;

public record ProductDetailResponse(
    ProductInfo product,
    PricingInfo pricing,
    List<Review> reviews,
    List<Recommendation> recommendations
) {}
