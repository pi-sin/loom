package io.loom.example.builder;

import io.loom.core.builder.BuilderContext;
import io.loom.core.builder.LoomBuilder;
import io.loom.example.dto.ProductInfo;
import org.springframework.stereotype.Component;

@Component
public class FetchProductBuilder implements LoomBuilder<ProductInfo> {

    @Override
    public ProductInfo build(BuilderContext context) {
        String id = context.getPathVariable("id");
        // In a real app: return context.service("product-service").route("get-product").get(ProductInfo.class);
        return new ProductInfo(id, "Premium Widget " + id,
                "A high-quality widget for all your needs", "Electronics");
    }
}
