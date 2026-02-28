package io.loom.core.service;

import java.util.List;
import java.util.Map;

/**
 * Unified response wrapper carrying typed data, HTTP status, headers, and raw body.
 * Used in both builder mode (typed exchange without throwing on 4xx/5xx) and
 * passthrough mode (raw byte forwarding with full response metadata).
 *
 * @param data        deserialized body (null on error or for raw byte mode)
 * @param statusCode  HTTP status code from upstream
 * @param headers     response headers from upstream
 * @param rawBody     raw response bytes (always available)
 * @param contentType response Content-Type from upstream
 */
public record ServiceResponse<T>(
        T data,
        int statusCode,
        Map<String, List<String>> headers,
        byte[] rawBody,
        String contentType
) {
    public boolean isSuccessful() {
        return statusCode >= 200 && statusCode < 300;
    }

    public boolean isClientError() {
        return statusCode >= 400 && statusCode < 500;
    }

    public boolean isServerError() {
        return statusCode >= 500;
    }
}
