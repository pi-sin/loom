package io.loom.core.engine;

import io.loom.core.exception.LoomServiceClientException;
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
    void shouldRethrowRuntimeExceptionSubclassDirectly() {
        AtomicInteger attempts = new AtomicInteger(0);

        assertThatThrownBy(() -> retryExecutor.execute(() -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("specific error");
        }, new RetryConfig(3, 10, 1.0, 100), "test"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("specific error");

        assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    void shouldWrapCheckedExceptionAfterMaxAttempts() {
        AtomicInteger attempts = new AtomicInteger(0);

        assertThatThrownBy(() -> retryExecutor.execute(() -> {
            attempts.incrementAndGet();
            throw new RuntimeException("always fail");
        }, new RetryConfig(3, 10, 1.0, 100), "test"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("always fail");

        assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    void shouldNotRetryWithNoRetryConfig() {
        AtomicInteger attempts = new AtomicInteger(0);

        assertThatThrownBy(() -> retryExecutor.execute(() -> {
            attempts.incrementAndGet();
            throw new RuntimeException("fail");
        }, RetryConfig.noRetry(), "test"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("fail");

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

    @Test
    void shouldPreserveLoomServiceClientExceptionThroughRetry() {
        AtomicInteger attempts = new AtomicInteger(0);
        Throwable rootCause = new RuntimeException("connection refused");

        assertThatThrownBy(() -> retryExecutor.execute(() -> {
            attempts.incrementAndGet();
            throw new LoomServiceClientException("payment-svc", 503, "Bad Gateway", rootCause);
        }, new RetryConfig(3, 10, 1.0, 100), "payment-call"))
                .isInstanceOf(LoomServiceClientException.class)
                .satisfies(ex -> {
                    LoomServiceClientException sce = (LoomServiceClientException) ex;
                    assertThat(sce.getServiceName()).isEqualTo("payment-svc");
                    assertThat(sce.getStatusCode()).isEqualTo(503);
                    assertThat(sce.getCause()).isSameAs(rootCause);
                });

        assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    void shouldPreserveCauseChainThroughRetry() {
        Throwable rootCause = new IllegalStateException("underlying issue");
        RuntimeException wrapper = new RuntimeException("wrapped error", rootCause);

        assertThatThrownBy(() -> retryExecutor.execute(() -> {
            throw wrapper;
        }, new RetryConfig(2, 10, 1.0, 100), "test-op"))
                .isSameAs(wrapper)
                .hasCause(rootCause);
    }

    @Test
    void shouldNotRetryOn4xxClientError() {
        AtomicInteger attempts = new AtomicInteger(0);

        assertThatThrownBy(() -> retryExecutor.execute(() -> {
            attempts.incrementAndGet();
            throw new LoomServiceClientException("user-svc", 404, "Not Found");
        }, new RetryConfig(3, 10, 1.0, 100), "test"))
                .isInstanceOf(LoomServiceClientException.class)
                .satisfies(ex -> {
                    LoomServiceClientException sce = (LoomServiceClientException) ex;
                    assertThat(sce.getStatusCode()).isEqualTo(404);
                });

        // Should fail immediately without retrying
        assertThat(attempts.get()).isEqualTo(1);
    }

    @Test
    void shouldRetryOn5xxServerError() {
        AtomicInteger attempts = new AtomicInteger(0);

        assertThatThrownBy(() -> retryExecutor.execute(() -> {
            attempts.incrementAndGet();
            throw new LoomServiceClientException("payment-svc", 503, "Service Unavailable");
        }, new RetryConfig(3, 10, 1.0, 100), "test"))
                .isInstanceOf(LoomServiceClientException.class)
                .satisfies(ex -> {
                    LoomServiceClientException sce = (LoomServiceClientException) ex;
                    assertThat(sce.getStatusCode()).isEqualTo(503);
                });

        // Should retry all 3 attempts
        assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    void shouldRetryOnTransportFailure() {
        AtomicInteger attempts = new AtomicInteger(0);

        assertThatThrownBy(() -> retryExecutor.execute(() -> {
            attempts.incrementAndGet();
            throw new LoomServiceClientException("user-svc", "Connection refused",
                    new java.io.IOException("Connection refused"));
        }, new RetryConfig(3, 10, 1.0, 100), "test"))
                .isInstanceOf(LoomServiceClientException.class)
                .satisfies(ex -> {
                    LoomServiceClientException sce = (LoomServiceClientException) ex;
                    assertThat(sce.getStatusCode()).isEqualTo(-1);
                });

        // Transport failures (statusCode=-1) should retry
        assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    void shouldNotRetryOn400BadRequest() {
        AtomicInteger attempts = new AtomicInteger(0);

        assertThatThrownBy(() -> retryExecutor.execute(() -> {
            attempts.incrementAndGet();
            throw new LoomServiceClientException("api-svc", 400, "Bad Request");
        }, new RetryConfig(3, 10, 1.0, 100), "test"))
                .isInstanceOf(LoomServiceClientException.class);

        assertThat(attempts.get()).isEqualTo(1);
    }

    @Test
    void shouldNotRetryOn401Unauthorized() {
        AtomicInteger attempts = new AtomicInteger(0);

        assertThatThrownBy(() -> retryExecutor.execute(() -> {
            attempts.incrementAndGet();
            throw new LoomServiceClientException("api-svc", 401, "Unauthorized");
        }, new RetryConfig(3, 10, 1.0, 100), "test"))
                .isInstanceOf(LoomServiceClientException.class);

        assertThat(attempts.get()).isEqualTo(1);
    }

    @Test
    void shouldNotRetryOn409Conflict() {
        AtomicInteger attempts = new AtomicInteger(0);

        assertThatThrownBy(() -> retryExecutor.execute(() -> {
            attempts.incrementAndGet();
            throw new LoomServiceClientException("api-svc", 409, "Conflict");
        }, new RetryConfig(3, 10, 1.0, 100), "test"))
                .isInstanceOf(LoomServiceClientException.class);

        assertThat(attempts.get()).isEqualTo(1);
    }
}
