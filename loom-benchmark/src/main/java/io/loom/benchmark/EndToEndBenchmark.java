package io.loom.benchmark;

import io.loom.core.annotation.LoomApi;
import io.loom.core.annotation.LoomGraph;
import io.loom.core.annotation.Node;
import io.loom.core.builder.BuilderContext;
import io.loom.core.builder.LoomBuilder;
import io.loom.core.codec.DslJsonCodec;
import io.loom.core.codec.JsonCodec;
import io.loom.core.engine.Dag;
import io.loom.core.engine.DagCompiler;
import io.loom.core.engine.DagExecutor;
import io.loom.core.exception.LoomDependencyResolutionException;
import io.loom.core.model.ApiDefinition;
import io.loom.core.registry.BuilderFactory;
import io.loom.core.service.ServiceAccessor;
import io.loom.starter.web.LoomHttpContextImpl;
import io.loom.starter.web.RouteTrie;

import org.openjdk.jmh.annotations.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Measures the full CPU-bound pipeline: route match -> context creation ->
 * DAG execution -> JSON serialization. No network I/O.
 *
 * If this achieves >50K ops/sec single-threaded, with virtual threads on
 * 4 vCPU the framework can sustain 20-30K+ real TPS (where most time is
 * spent waiting on upstream I/O, not CPU work).
 */
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class EndToEndBenchmark {

    // ── Types ──

    public record UserData(String id, String name) {}
    public record ConfigData(String region) {}
    public record EnrichedData(String id, String name, String region) {}
    public record OrderData(List<String> orderIds) {}
    public record ApiResponse(String userId, String region, int orderCount) {}

    // ── Builders (no I/O) ──

    public static class FetchUserBuilder implements LoomBuilder<UserData> {
        public UserData build(BuilderContext ctx) { return new UserData("u-1", "Alice"); }
    }
    public static class FetchConfigBuilder implements LoomBuilder<ConfigData> {
        public ConfigData build(BuilderContext ctx) { return new ConfigData("us-east-1"); }
    }
    public static class EnrichBuilder implements LoomBuilder<EnrichedData> {
        public EnrichedData build(BuilderContext ctx) {
            UserData u = ctx.getDependency(UserData.class);
            ConfigData c = ctx.getDependency(ConfigData.class);
            return new EnrichedData(u.id(), u.name(), c.region());
        }
    }
    public static class FetchOrdersBuilder implements LoomBuilder<OrderData> {
        public OrderData build(BuilderContext ctx) {
            return new OrderData(List.of("o-1", "o-2", "o-3"));
        }
    }
    public static class AssembleBuilder implements LoomBuilder<ApiResponse> {
        public ApiResponse build(BuilderContext ctx) {
            EnrichedData e = ctx.getDependency(EnrichedData.class);
            OrderData o = ctx.getDependency(OrderData.class);
            return new ApiResponse(e.id(), e.region(), o.orderIds().size());
        }
    }

    @LoomApi(method = "GET", path = "/api/users/{userId}/dashboard", response = ApiResponse.class)
    @LoomGraph({
        @Node(builder = FetchUserBuilder.class),
        @Node(builder = FetchConfigBuilder.class),
        @Node(builder = EnrichBuilder.class, dependsOn = {FetchUserBuilder.class, FetchConfigBuilder.class}),
        @Node(builder = FetchOrdersBuilder.class, dependsOn = FetchUserBuilder.class),
        @Node(builder = AssembleBuilder.class, dependsOn = {EnrichBuilder.class, FetchOrdersBuilder.class})
    })
    static class BenchmarkApi {}

    // ── State ──

    private RouteTrie routeTrie;
    private DagExecutor executor;
    private Dag dag;
    private JsonCodec codec;
    private MockHttpServletResponse mockResponse;

    @Setup
    public void setup() {
        codec = new DslJsonCodec();
        mockResponse = new MockHttpServletResponse();

        // Route trie with the benchmark route + some others for realism
        routeTrie = new RouteTrie();
        ApiDefinition benchApi = new ApiDefinition("GET", "/api/users/{userId}/dashboard",
                null, ApiResponse.class, null, null, null, null, null, null, null, null, null, null, null);
        routeTrie.insert(benchApi);

        // Add some filler routes
        for (int i = 0; i < 30; i++) {
            routeTrie.insert(new ApiDefinition("GET", "/api/resource" + i + "/{id}",
                    null, null, null, null, null, null, null, null, null, null, null, null, null));
        }

        BuilderFactory factory = new BuilderFactory() {
            @SuppressWarnings("unchecked")
            @Override
            public <T> LoomBuilder<T> createBuilder(Class<? extends LoomBuilder<T>> type) {
                return (LoomBuilder<T>) createBuilderUntyped(type);
            }
            @Override
            public LoomBuilder<?> createBuilderUntyped(Class<? extends LoomBuilder<?>> type) {
                if (type == FetchUserBuilder.class) return new FetchUserBuilder();
                if (type == FetchConfigBuilder.class) return new FetchConfigBuilder();
                if (type == EnrichBuilder.class) return new EnrichBuilder();
                if (type == FetchOrdersBuilder.class) return new FetchOrdersBuilder();
                if (type == AssembleBuilder.class) return new AssembleBuilder();
                throw new IllegalArgumentException("Unknown builder: " + type);
            }
        };

        DagCompiler compiler = new DagCompiler();
        dag = compiler.compile(BenchmarkApi.class);
        executor = new DagExecutor(factory);
    }

    @Benchmark
    public byte[] endToEnd() throws IOException {
        // 1. Route matching
        RouteTrie.RouteMatch match = routeTrie.find("GET", "/api/users/42/dashboard");

        // 2. Context creation (using mock request)
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/42/dashboard");
        request.addHeader("Accept", "application/json");
        request.addHeader("Authorization", "Bearer tok-123");
        LoomHttpContextImpl httpCtx = new LoomHttpContextImpl(
                request, mockResponse, codec, match.pathVariables(), 10_485_760);

        // 3. Build a lightweight BuilderContext from the HTTP context
        StubBuilderContext builderCtx = new StubBuilderContext(
                httpCtx.getHttpMethod(), httpCtx.getRequestPath(), match.pathVariables());

        // 4. DAG execution
        Object result = executor.execute(dag, builderCtx);

        // 5. JSON serialization
        return codec.writeValueAsBytes(result);
    }

    // ── Minimal BuilderContext ──

    static class StubBuilderContext implements BuilderContext {
        private final String method;
        private final String path;
        private final Map<String, String> pathVars;
        private Object[] results;
        private Map<Class<?>, Integer> typeIndexMap;
        private Map<Class<? extends LoomBuilder<?>>, Integer> builderIndexMap;
        private static final Object NULL_SENTINEL = new Object();

        StubBuilderContext(String method, String path, Map<String, String> pathVars) {
            this.method = method;
            this.path = path;
            this.pathVars = pathVars;
        }

        @Override public <T> T getRequestBody(Class<T> type) { return null; }
        @Override public String getPathVariable(String name) { return pathVars.get(name); }
        @Override public String getQueryParam(String name) { return null; }
        @Override public String getHeader(String name) { return null; }
        @Override public String getHttpMethod() { return method; }
        @Override public String getRequestPath() { return path; }
        @Override public Map<String, String> getPathVariables() { return pathVars; }
        @Override public Map<String, List<String>> getQueryParams() { return Map.of(); }
        @Override public Map<String, List<String>> getHeaders() { return Map.of(); }
        @Override public byte[] getRawRequestBody() { return null; }
        @Override public ServiceAccessor service(String name) { return null; }
        @Override public void setAttribute(String key, Object value) {}
        @Override @SuppressWarnings("unchecked")
        public <T> T getAttribute(String key, Class<T> type) { return null; }
        @Override public Map<String, Object> getAttributes() { return Map.of(); }

        @Override
        public void initResultStorage(int nodeCount,
                                      Map<Class<?>, Integer> typeIndexMap,
                                      Map<Class<? extends LoomBuilder<?>>, Integer> builderIndexMap) {
            this.results = new Object[nodeCount];
            this.typeIndexMap = typeIndexMap;
            this.builderIndexMap = builderIndexMap;
        }

        @Override
        public void storeResult(Class<? extends LoomBuilder<?>> builderClass, Class<?> outputType, Object result) {
            Object stored = result != null ? result : NULL_SENTINEL;
            Integer index = builderIndexMap.get(builderClass);
            if (index != null) results[index] = stored;
        }

        @Override @SuppressWarnings("unchecked")
        public <T> T getDependency(Class<T> outputType) {
            Integer index = typeIndexMap.get(outputType);
            if (index == null || results[index] == null) {
                throw new LoomDependencyResolutionException(outputType.getSimpleName(), List.of(), List.of());
            }
            Object r = results[index];
            return r == NULL_SENTINEL ? null : (T) r;
        }

        @Override @SuppressWarnings("unchecked")
        public <T> T getResultOf(Class<? extends LoomBuilder<T>> builderClass) {
            Integer index = builderIndexMap.get(builderClass);
            if (index == null || results[index] == null) {
                throw new LoomDependencyResolutionException("builder:" + builderClass.getSimpleName(), List.of(), List.of());
            }
            Object r = results[index];
            return r == NULL_SENTINEL ? null : (T) r;
        }

        @Override @SuppressWarnings("unchecked")
        public <T> Optional<T> getOptionalDependency(Class<T> outputType) {
            Integer index = typeIndexMap != null ? typeIndexMap.get(outputType) : null;
            if (index == null) return Optional.empty();
            Object v = results[index];
            return v == null ? Optional.empty() : Optional.ofNullable(v == NULL_SENTINEL ? null : (T) v);
        }

        @Override @SuppressWarnings("unchecked")
        public <T> Optional<T> getOptionalResultOf(Class<? extends LoomBuilder<T>> builderClass) {
            Integer index = builderIndexMap != null ? builderIndexMap.get(builderClass) : null;
            if (index == null) return Optional.empty();
            Object v = results[index];
            return v == null ? Optional.empty() : Optional.ofNullable(v == NULL_SENTINEL ? null : (T) v);
        }
    }
}
