package io.loom.core.upstream;

public record UpstreamConfig(
    String name,
    String baseUrl,
    long connectTimeoutMs,
    long readTimeoutMs,
    RetryConfig retry
) {
    public UpstreamConfig(String name, String baseUrl) {
        this(name, baseUrl, 5000, 30000, RetryConfig.defaults());
    }
}
