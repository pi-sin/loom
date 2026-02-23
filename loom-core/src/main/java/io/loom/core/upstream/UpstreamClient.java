package io.loom.core.upstream;

import java.util.Map;

public interface UpstreamClient {
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
}
