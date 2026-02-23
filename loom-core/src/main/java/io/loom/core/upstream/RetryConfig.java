package io.loom.core.upstream;

public record RetryConfig(
    int maxAttempts,
    long initialDelayMs,
    double multiplier,
    long maxDelayMs
) {
    public static RetryConfig defaults() {
        return new RetryConfig(3, 100, 2.0, 5000);
    }

    public static RetryConfig noRetry() {
        return new RetryConfig(1, 0, 1.0, 0);
    }
}
