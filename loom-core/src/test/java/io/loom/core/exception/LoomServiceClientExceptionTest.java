package io.loom.core.exception;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class LoomServiceClientExceptionTest {

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 404, 405, 409, 422, 429, 499})
    void clientErrors_notRetryable(int status) {
        var ex = new LoomServiceClientException("svc", status, "error");
        assertThat(ex.isRetryable()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 502, 503, 504, 599})
    void serverErrors_retryable(int status) {
        var ex = new LoomServiceClientException("svc", status, "error");
        assertThat(ex.isRetryable()).isTrue();
    }

    @Test
    void transportFailure_retryable() {
        var ex = new LoomServiceClientException("svc", "Connection refused",
                new java.io.IOException("Connection refused"));
        assertThat(ex.getStatusCode()).isEqualTo(-1);
        assertThat(ex.isRetryable()).isTrue();
    }

    @Test
    void httpErrorConstructor_preservesFields() {
        var cause = new RuntimeException("root");
        var ex = new LoomServiceClientException("payment-svc", 502, "Bad Gateway", cause);
        assertThat(ex.getServiceName()).isEqualTo("payment-svc");
        assertThat(ex.getStatusCode()).isEqualTo(502);
        assertThat(ex.getCause()).isSameAs(cause);
        assertThat(ex.getMessage()).contains("payment-svc").contains("502");
    }

    @Test
    void transportErrorConstructor_setsStatusMinusOne() {
        var ex = new LoomServiceClientException("svc", "timeout", new RuntimeException());
        assertThat(ex.getStatusCode()).isEqualTo(-1);
        assertThat(ex.getMessage()).contains("svc").contains("timeout");
    }
}
