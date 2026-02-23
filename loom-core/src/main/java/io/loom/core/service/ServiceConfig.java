package io.loom.core.service;

public record ServiceConfig(
    String name,
    String baseUrl,
    long connectTimeoutMs,
    long readTimeoutMs,
    RetryConfig retry
) {
    public ServiceConfig(String name, String baseUrl) {
        this(name, baseUrl, 5000, 30000, RetryConfig.defaults());
    }
}
