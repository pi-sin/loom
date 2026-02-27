package io.loom.core.engine;

import io.loom.core.service.RetryConfig;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

@Slf4j
public class RetryExecutor {

    public <T> T execute(Supplier<T> action, RetryConfig config, String operationName) {
        Throwable lastException = null;

        for (int attempt = 0; attempt < config.maxAttempts(); attempt++) {
            try {
                return action.get();
            } catch (Exception e) {
                lastException = e;
                if (attempt < config.maxAttempts() - 1) {
                    long delay = calculateDelay(attempt, config);
                    log.warn("[Loom] Retry attempt {}/{} for '{}' after {}ms: {}",
                            attempt + 1, config.maxAttempts(), operationName, delay, e.getMessage());
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Retry interrupted for: " + operationName, ie);
                    }
                }
            }
        }

        if (lastException instanceof RuntimeException re) {
            throw re;
        }
        throw new RuntimeException(
                "All " + config.maxAttempts() + " attempts failed for: " + operationName, lastException);
    }

    long calculateDelay(int attempt, RetryConfig config) {
        double baseDelay = config.initialDelayMs() * Math.pow(config.multiplier(), attempt);
        double jitter = baseDelay * 0.2 * ThreadLocalRandom.current().nextDouble();
        long delay = (long) (baseDelay + jitter);
        return Math.min(delay, config.maxDelayMs());
    }
}
