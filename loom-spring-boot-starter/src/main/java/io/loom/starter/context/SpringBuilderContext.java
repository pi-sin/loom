package io.loom.starter.context;

import io.loom.core.builder.BuilderContext;
import io.loom.core.builder.LoomBuilder;
import io.loom.core.codec.JsonCodec;
import io.loom.core.exception.DependencyResolutionException;
import io.loom.core.exception.LoomException;
import io.loom.core.service.ServiceClient;
import io.loom.starter.service.ServiceClientRegistry;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SpringBuilderContext implements BuilderContext {

    private final String httpMethod;
    private final String requestPath;
    private final Map<String, String> pathVariables;
    private final Map<String, List<String>> queryParams;
    private final Map<String, List<String>> headers;
    private final byte[] rawRequestBody;
    private final JsonCodec jsonCodec;
    private final ServiceClientRegistry serviceRegistry;
    private final String requestId;
    private final Object cachedRequestBody;

    private final Map<String, String> unmodPathVars;
    private final Map<String, List<String>> unmodQueryParams;
    private final Map<String, List<String>> unmodHeaders;

    private final ConcurrentHashMap<String, Object> attributes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Class<?>, Object> resultsByType = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Class<? extends LoomBuilder<?>>, Object> resultsByBuilder = new ConcurrentHashMap<>();

    public SpringBuilderContext(String httpMethod, String requestPath,
                                Map<String, String> pathVariables,
                                Map<String, List<String>> queryParams,
                                Map<String, List<String>> headers,
                                byte[] rawRequestBody,
                                JsonCodec jsonCodec,
                                ServiceClientRegistry serviceRegistry,
                                String requestId,
                                Object cachedRequestBody) {
        this.httpMethod = httpMethod;
        this.requestPath = requestPath;
        this.pathVariables = pathVariables != null ? pathVariables : Map.of();
        this.queryParams = queryParams != null ? queryParams : Map.of();
        this.headers = headers != null ? headers : Map.of();
        this.rawRequestBody = rawRequestBody;
        this.jsonCodec = jsonCodec;
        this.serviceRegistry = serviceRegistry;
        this.requestId = requestId;
        this.cachedRequestBody = cachedRequestBody;
        this.unmodPathVars = Collections.unmodifiableMap(this.pathVariables);
        this.unmodQueryParams = Collections.unmodifiableMap(this.queryParams);
        this.unmodHeaders = Collections.unmodifiableMap(this.headers);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getRequestBody(Class<T> type) {
        if (cachedRequestBody != null && type.isInstance(cachedRequestBody)) {
            return (T) cachedRequestBody;
        }
        if (rawRequestBody == null || rawRequestBody.length == 0) {
            return null;
        }
        try {
            return jsonCodec.readValue(rawRequestBody, type);
        } catch (Exception e) {
            throw new LoomException("Failed to deserialize request body to " + type.getSimpleName(), e);
        }
    }

    @Override
    public String getPathVariable(String name) {
        return pathVariables.get(name);
    }

    @Override
    public String getQueryParam(String name) {
        List<String> values = queryParams.get(name);
        return values != null && !values.isEmpty() ? values.get(0) : null;
    }

    @Override
    public String getHeader(String name) {
        List<String> values = headers.get(name);
        return values != null && !values.isEmpty() ? values.get(0) : null;
    }

    @Override
    public String getHttpMethod() {
        return httpMethod;
    }

    @Override
    public String getRequestPath() {
        return requestPath;
    }

    @Override
    public Map<String, String> getPathVariables() {
        return unmodPathVars;
    }

    @Override
    public Map<String, List<String>> getQueryParams() {
        return unmodQueryParams;
    }

    @Override
    public Map<String, List<String>> getHeaders() {
        return unmodHeaders;
    }

    @Override
    public byte[] getRawRequestBody() {
        return rawRequestBody;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getDependency(Class<T> outputType) {
        Object result = resultsByType.get(outputType);
        if (result == null) {
            throw new DependencyResolutionException(
                    outputType.getSimpleName(),
                    resultsByType.keySet().stream().map(Class::getSimpleName).toList(),
                    resultsByBuilder.keySet().stream().map(Class::getSimpleName).toList());
        }
        return (T) result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getResultOf(Class<? extends LoomBuilder<T>> builderClass) {
        Object result = resultsByBuilder.get(builderClass);
        if (result == null) {
            throw new DependencyResolutionException(
                    "builder:" + builderClass.getSimpleName(),
                    resultsByType.keySet().stream().map(Class::getSimpleName).toList(),
                    resultsByBuilder.keySet().stream().map(Class::getSimpleName).toList());
        }
        return (T) result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getOptionalDependency(Class<T> outputType) {
        return Optional.ofNullable((T) resultsByType.get(outputType));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getOptionalResultOf(Class<? extends LoomBuilder<T>> builderClass) {
        return Optional.ofNullable((T) resultsByBuilder.get(builderClass));
    }

    @Override
    public ServiceClient service(String name) {
        return serviceRegistry.getClient(name);
    }

    @Override
    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key, Class<T> type) {
        return (T) attributes.get(key);
    }

    @Override
    public Map<String, Object> getAttributes() {
        return Collections.unmodifiableMap(attributes);
    }

    @Override
    public String getRequestId() {
        return requestId;
    }

    // Methods for the executor to store results
    public void storeResult(Class<? extends LoomBuilder<?>> builderClass, Class<?> outputType, Object result) {
        resultsByBuilder.put(builderClass, result);
        resultsByType.put(outputType, result);
    }
}
