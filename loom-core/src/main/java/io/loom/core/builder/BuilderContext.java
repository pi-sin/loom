package io.loom.core.builder;

import io.loom.core.service.ServiceAccessor;
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
    <T> T getResultOf(Class<? extends LoomBuilder<T>> builderClass);
    <T> Optional<T> getOptionalDependency(Class<T> outputType);
    <T> Optional<T> getOptionalResultOf(Class<? extends LoomBuilder<T>> builderClass);

    // Service accessor (route-based)
    ServiceAccessor service(String name);

    // Custom attributes
    void setAttribute(String key, Object value);
    <T> T getAttribute(String key, Class<T> type);
    Map<String, Object> getAttributes();

    // Result storage (used by DagExecutor)
    void storeResult(Class<? extends LoomBuilder<?>> builderClass, Class<?> outputType, Object result);

    /**
     * Called once by {@link io.loom.core.engine.DagExecutor} before DAG execution to initialize
     * pre-indexed, array-based result storage. The executor passes the compiled index maps so that
     * {@link #storeResult}, {@link #getDependency}, {@link #getResultOf}, and their optional
     * variants can resolve results by array index instead of concurrent map lookup.
     *
     * <p>The default implementation is a no-op for backward compatibility with test stubs that
     * use their own storage. Any {@code BuilderContext} implementation used with
     * {@code DagExecutor} in production <b>must</b> override this method to allocate the
     * result array and store the index maps; otherwise dependency resolution will fail at runtime.
     *
     * @param nodeCount       total number of nodes in the DAG (array size)
     * @param typeIndexMap    output type → array index (for type-based lookups)
     * @param builderIndexMap builder class → array index (for builder-class-based lookups)
     */
    default void initResultStorage(int nodeCount,
                                   Map<Class<?>, Integer> typeIndexMap,
                                   Map<Class<? extends LoomBuilder<?>>, Integer> builderIndexMap) {
        // no-op default for backward compatibility
    }
}
