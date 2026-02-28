package io.loom.core.service;

import java.util.Map;

public interface ServiceClient {
    <T> T get(String path, Class<T> responseType);
    <T> T get(String path, Class<T> responseType, Map<String, String> headers);

    <T> T post(String path, Object body, Class<T> responseType);
    <T> T post(String path, Object body, Class<T> responseType, Map<String, String> headers);

    <T> T put(String path, Object body, Class<T> responseType);
    <T> T put(String path, Object body, Class<T> responseType, Map<String, String> headers);

    <T> T delete(String path, Class<T> responseType);
    <T> T delete(String path, Class<T> responseType, Map<String, String> headers);

    <T> T patch(String path, Object body, Class<T> responseType);
    <T> T patch(String path, Object body, Class<T> responseType, Map<String, String> headers);

    /**
     * Raw proxy for passthrough mode — returns raw bytes with full response metadata.
     * Does not throw on 4xx/5xx; upstream status is captured in the response.
     */
    default ServiceResponse<byte[]> proxy(String method, String path, byte[] body, Map<String, String> headers) {
        throw new UnsupportedOperationException("proxy() not implemented by " + getClass().getSimpleName());
    }

    /**
     * Typed exchange for builder mode — returns typed data with full response metadata.
     * Does not throw on 4xx/5xx; upstream status is captured in the response.
     * Transport failures still throw LoomServiceClientException.
     */
    default <T> ServiceResponse<T> exchange(String method, String path, Object body,
                                             Class<T> responseType, Map<String, String> headers) {
        throw new UnsupportedOperationException("exchange() not implemented by " + getClass().getSimpleName());
    }
}
