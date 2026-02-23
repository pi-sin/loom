package io.loom.core.engine;

import io.loom.core.service.RetryConfig;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

class RetryExecutorTest {

    private final RetryExecutor retryExecutor = new RetryExecutor();

    @Test
    void shouldSucceedOnFirstAttempt() {
        String result = retryExecutor.execute(() -> "success",
                RetryConfig.defaults(), "test");
        assertThat(result).isEqualTo("success");
    }

    @Test
    void shouldRetryOnFailure() {
        AtomicInteger attempts = new AtomicInteger(0);

        String result = retryExecutor.execute(() -> {
            if (attempts.incrementAndGet() < 3) {
                throw new RuntimeException("fail");
            }
            return "success after retries";
        }, RetryConfig.defaults(), "test");

        assertThat(result).isEqualTo("success after retries");
        assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    void shouldThrowAfterMaxAttempts() {
        AtomicInteger attempts = new AtomicInteger(0);

        assertThatThrownBy(() -> retryExecutor.execute(() -> {
            attempts.incrementAndGet();
            throw new RuntimeException("always fail");
        }, new RetryConfig(3, 10, 1.0, 100), "test"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("All 3 attempts failed");

        assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    void shouldNotRetryWithNoRetryConfig() {
        AtomicInteger attempts = new AtomicInteger(0);

        assertThatThrownBy(() -> retryExecutor.execute(() -> {
            attempts.incrementAndGet();
            throw new RuntimeException("fail");
        }, RetryConfig.noRetry(), "test"))
                .isInstanceOf(RuntimeException.class);

        assertThat(attempts.get()).isEqualTo(1);
    }

    @Test
    void shouldCalculateExponentialBackoff() {
        RetryConfig config = new RetryConfig(5, 100, 2.0, 5000);

        long delay0 = retryExecutor.calculateDelay(0, config);
        long delay1 = retryExecutor.calculateDelay(1, config);
        long delay2 = retryExecutor.calculateDelay(2, config);

        // With jitter, delays should be approximately: 100, 200, 400
        assertThat(delay0).isBetween(100L, 125L);
        assertThat(delay1).isBetween(200L, 250L);
        assertThat(delay2).isBetween(400L, 500L);
    }

    @Test
    void shouldRespectMaxDelay() {
        RetryConfig config = new RetryConfig(5, 1000, 10.0, 2000);

        long delay = retryExecutor.calculateDelay(5, config);
        assertThat(delay).isLessThanOrEqualTo(2000);
    }
}
