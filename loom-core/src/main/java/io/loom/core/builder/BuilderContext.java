package io.loom.core.builder;

import io.loom.core.upstream.UpstreamClient;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface BuilderContext {
    // Request data
    <T> T getRequestBody(Class<T> type);
    String getPathVariable(String name);
    String getQueryParam(String name);
    String getHeader(String name);
    String getHttpMethod();
    String getRequestPath();
    Map<String, String> getPathVariables();
    Map<String, List<String>> getQueryParams();
    Map<String, List<String>> getHeaders();
    byte[] getRawRequestBody();

    // Dependency outputs
    <T> T getDependency(Class<T> outputType);
    <T> T resultOf(Class<? extends LoomBuilder<T>> builderClass);
    <T> Optional<T> optionalResultOf(Class<? extends LoomBuilder<T>> builderClass);

    // Upstream HTTP client
    UpstreamClient upstream(String name);

    // Custom attributes
    void setAttribute(String key, Object value);
    <T> T getAttribute(String key, Class<T> type);
    Map<String, Object> getAttributes();

    // Correlation ID
    String getRequestId();
}
