package io.loom.benchmark;

import io.loom.core.codec.DslJsonCodec;
import io.loom.core.codec.JsonCodec;

import org.openjdk.jmh.annotations.*;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class JsonCodecBenchmark {

    // Typical API response POJO (~500 bytes serialized)
    public record Product(
        String id,
        String name,
        String description,
        double price,
        String currency,
        int stockCount,
        List<String> tags,
        Dimensions dimensions
    ) {}

    public record Dimensions(double width, double height, double depth, String unit) {}

    private JsonCodec codec;
    private Product product;
    private byte[] serializedBytes;

    @Setup
    public void setup() throws IOException {
        codec = new DslJsonCodec();
        product = new Product(
            "prod-12345",
            "Premium Wireless Headphones",
            "High-fidelity audio with active noise cancellation and 30-hour battery life",
            299.99,
            "USD",
            1500,
            List.of("electronics", "audio", "wireless", "premium"),
            new Dimensions(18.5, 20.0, 8.0, "cm")
        );
        serializedBytes = codec.writeValueAsBytes(product);
    }

    @Benchmark
    public byte[] serialize() throws IOException {
        return codec.writeValueAsBytes(product);
    }

    @Benchmark
    public Product deserialize() throws IOException {
        return codec.readValue(serializedBytes, Product.class);
    }
}
