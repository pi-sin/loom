package io.loom.starter.context;

import io.loom.core.builder.BuilderContext;
import io.loom.core.builder.LoomBuilder;
import io.loom.core.codec.JsonCodec;
import io.loom.core.exception.LoomDependencyResolutionException;
import io.loom.core.exception.LoomException;
import io.loom.core.service.ServiceAccessor;
import io.loom.starter.service.ServiceAccessorImpl;
import io.loom.starter.service.ServiceClientRegistry;

import java.util.*;

public class SpringBuilderContext implements BuilderContext {

    private final String httpMethod;
    private final String requestPath;
    private final Map<String, String> pathVariables;
    private final Map<String, List<String>> queryParams;
    private final Map<String, List<String>> headers;
    private final byte[] rawRequestBody;
    private final JsonCodec jsonCodec;
    private final ServiceClientRegistry serviceRegistry;
    private final Object cachedRequestBody;

    private final Map<String, String> unmodPathVars;
    private final Map<String, List<String>> unmodQueryParams;
    private final Map<String, List<String>> unmodHeaders;

    private final Map<String, Object> attributes = new HashMap<>();
    private static final Object NULL_SENTINEL = new Object();

    // Array-based result storage (initialized by DagExecutor before DAG execution)
    private volatile Object[] results;
    private Map<Class<?>, Integer> typeIndexMap;
    private Map<Class<? extends LoomBuilder<?>>, Integer> builderIndexMap;

    public SpringBuilderContext(String httpMethod, String requestPath,
                                Map<String, String> pathVariables,
                                Map<String, List<String>> queryParams,
                                Map<String, List<String>> headers,
                                byte[] rawRequestBody,
                                JsonCodec jsonCodec,
                                ServiceClientRegistry serviceRegistry,
                                Object cachedRequestBody) {
        this.httpMethod = httpMethod;
        this.requestPath = requestPath;
        this.pathVariables = pathVariables != null ? pathVariables : Map.of();
        this.queryParams = queryParams != null ? queryParams : Map.of();
        this.headers = headers != null ? headers : Map.of();
        this.rawRequestBody = rawRequestBody;
        this.jsonCodec = jsonCodec;
        this.serviceRegistry = serviceRegistry;
        this.cachedRequestBody = cachedRequestBody;
        this.unmodPathVars = this.pathVariables;
        this.unmodQueryParams = this.queryParams;
        this.unmodHeaders = this.headers;
    }

    @Override
    public void initResultStorage(int nodeCount,
                                  Map<Class<?>, Integer> typeIndexMap,
                                  Map<Class<? extends LoomBuilder<?>>, Integer> builderIndexMap) {
        this.results = new Object[nodeCount];
        this.typeIndexMap = typeIndexMap;
        this.builderIndexMap = builderIndexMap;
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
        Integer index = typeIndexMap.get(outputType);
        if (index == null) {
            throw new LoomDependencyResolutionException(
                    outputType.getSimpleName(),
                    availableTypeNames(),
                    availableBuilderNames());
        }
        Object result = results[index];
        if (result == null) {
            throw new LoomDependencyResolutionException(
                    outputType.getSimpleName(),
                    availableTypeNames(),
                    availableBuilderNames());
        }
        return result == NULL_SENTINEL ? null : (T) result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getResultOf(Class<? extends LoomBuilder<T>> builderClass) {
        Integer index = builderIndexMap.get(builderClass);
        if (index == null) {
            throw new LoomDependencyResolutionException(
                    "builder:" + builderClass.getSimpleName(),
                    availableTypeNames(),
                    availableBuilderNames());
        }
        Object result = results[index];
        if (result == null) {
            throw new LoomDependencyResolutionException(
                    "builder:" + builderClass.getSimpleName(),
                    availableTypeNames(),
                    availableBuilderNames());
        }
        return result == NULL_SENTINEL ? null : (T) result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getOptionalDependency(Class<T> outputType) {
        Integer index = typeIndexMap.get(outputType);
        if (index == null) {
            return Optional.empty();
        }
        Object value = results[index];
        return value == null ? Optional.empty() : Optional.ofNullable(value == NULL_SENTINEL ? null : (T) value);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getOptionalResultOf(Class<? extends LoomBuilder<T>> builderClass) {
        Integer index = builderIndexMap.get(builderClass);
        if (index == null) {
            return Optional.empty();
        }
        Object value = results[index];
        return value == null ? Optional.empty() : Optional.ofNullable(value == NULL_SENTINEL ? null : (T) value);
    }

    @Override
    public ServiceAccessor service(String name) {
        return new ServiceAccessorImpl(name, serviceRegistry, pathVariables, queryParams);
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
    public void storeResult(Class<? extends LoomBuilder<?>> builderClass, Class<?> outputType, Object result) {
        Object stored = result != null ? result : NULL_SENTINEL;
        Integer index = builderIndexMap.get(builderClass);
        if (index != null) {
            results[index] = stored;
        }
    }

    private List<String> availableTypeNames() {
        List<String> names = new ArrayList<>();
        for (var entry : typeIndexMap.entrySet()) {
            if (results[entry.getValue()] != null) {
                names.add(entry.getKey().getSimpleName());
            }
        }
        return names;
    }

    private List<String> availableBuilderNames() {
        List<String> names = new ArrayList<>();
        for (var entry : builderIndexMap.entrySet()) {
            if (results[entry.getValue()] != null) {
                names.add(entry.getKey().getSimpleName());
            }
        }
        return names;
    }
}
