package io.loom.core.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceResponseTest {

    @Test
    void isSuccessful_200() {
        var resp = response(200);
        assertThat(resp.isSuccessful()).isTrue();
        assertThat(resp.isClientError()).isFalse();
        assertThat(resp.isServerError()).isFalse();
    }

    @Test
    void isSuccessful_299() {
        assertThat(response(299).isSuccessful()).isTrue();
    }

    @Test
    void isSuccessful_199_isFalse() {
        assertThat(response(199).isSuccessful()).isFalse();
    }

    @Test
    void isSuccessful_300_isFalse() {
        assertThat(response(300).isSuccessful()).isFalse();
    }

    @Test
    void isClientError_400() {
        var resp = response(400);
        assertThat(resp.isClientError()).isTrue();
        assertThat(resp.isSuccessful()).isFalse();
        assertThat(resp.isServerError()).isFalse();
    }

    @Test
    void isClientError_499() {
        assertThat(response(499).isClientError()).isTrue();
    }

    @Test
    void isClientError_399_isFalse() {
        assertThat(response(399).isClientError()).isFalse();
    }

    @Test
    void isClientError_500_isFalse() {
        assertThat(response(500).isClientError()).isFalse();
    }

    @Test
    void isServerError_500() {
        var resp = response(500);
        assertThat(resp.isServerError()).isTrue();
        assertThat(resp.isSuccessful()).isFalse();
        assertThat(resp.isClientError()).isFalse();
    }

    @Test
    void isServerError_599() {
        assertThat(response(599).isServerError()).isTrue();
    }

    @Test
    void isServerError_499_isFalse() {
        assertThat(response(499).isServerError()).isFalse();
    }

    @Test
    void nullDataAndHeaders() {
        var resp = new ServiceResponse<String>(null, 404, null, null, null);
        assertThat(resp.data()).isNull();
        assertThat(resp.headers()).isNull();
        assertThat(resp.rawBody()).isNull();
        assertThat(resp.contentType()).isNull();
        assertThat(resp.isClientError()).isTrue();
    }

    @Test
    void emptyRawBody() {
        var resp = new ServiceResponse<>("data", 200, Map.of(), new byte[0], "application/json");
        assertThat(resp.data()).isEqualTo("data");
        assertThat(resp.rawBody()).isEmpty();
    }

    @Test
    void multiValueHeaders() {
        Map<String, List<String>> headers = Map.of(
                "Set-Cookie", List.of("a=1", "b=2"),
                "X-Single", List.of("val")
        );
        var resp = new ServiceResponse<>(null, 200, headers, new byte[0], "text/plain");
        assertThat(resp.headers().get("Set-Cookie")).containsExactly("a=1", "b=2");
        assertThat(resp.headers().get("X-Single")).containsExactly("val");
    }

    private ServiceResponse<String> response(int statusCode) {
        return new ServiceResponse<>("data", statusCode, Map.of(), "data".getBytes(), "application/json");
    }
}
