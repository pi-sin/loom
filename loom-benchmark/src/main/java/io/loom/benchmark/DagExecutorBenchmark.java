package io.loom.benchmark;

import io.loom.core.builder.BuilderContext;
import io.loom.core.builder.LoomBuilder;
import io.loom.core.engine.Dag;
import io.loom.core.engine.DagCompiler;
import io.loom.core.engine.DagExecutor;
import io.loom.core.annotation.LoomApi;
import io.loom.core.annotation.LoomGraph;
import io.loom.core.annotation.Node;
import io.loom.core.exception.LoomDependencyResolutionException;
import io.loom.core.registry.BuilderFactory;
import io.loom.core.service.ServiceAccessor;

import org.openjdk.jmh.annotations.*;

import java.util.*;
import java.util.concurrent.TimeUnit;

@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class DagExecutorBenchmark {

    // ── Response types ──

    public record UserData(String id, String name) {}
    public record ConfigData(String region) {}
    public record EnrichedData(String id, String name, String region) {}
    public record OrderData(List<String> orderIds) {}
    public record DashboardResponse(String userId, String region, int orderCount) {}

    // ── Mock builders (instant return, no I/O) ──

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
    public static class AssembleBuilder implements LoomBuilder<DashboardResponse> {
        public DashboardResponse build(BuilderContext ctx) {
            EnrichedData e = ctx.getDependency(EnrichedData.class);
            OrderData o = ctx.getDependency(OrderData.class);
            return new DashboardResponse(e.id(), e.region(), o.orderIds().size());
        }
    }

    // ── DAG definition via annotations ──

    @LoomApi(method = "GET", path = "/benchmark/dashboard", response = DashboardResponse.class)
    @LoomGraph({
        @Node(builder = FetchUserBuilder.class),
        @Node(builder = FetchConfigBuilder.class),
        @Node(builder = EnrichBuilder.class, dependsOn = {FetchUserBuilder.class, FetchConfigBuilder.class}),
        @Node(builder = FetchOrdersBuilder.class, dependsOn = FetchUserBuilder.class),
        @Node(builder = AssembleBuilder.class, dependsOn = {EnrichBuilder.class, FetchOrdersBuilder.class})
    })
    static class BenchmarkApi {}

    private DagExecutor executor;
    private Dag dag;

    @Setup
    public void setup() {
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
    public Object executeDag() {
        return executor.execute(dag, new StubBuilderContext());
    }

    // ── Minimal BuilderContext for benchmarking ──

    static class StubBuilderContext implements BuilderContext {
        private Object[] results;
        private Map<Class<?>, Integer> typeIndexMap;
        private Map<Class<? extends LoomBuilder<?>>, Integer> builderIndexMap;
        private static final Object NULL_SENTINEL = new Object();

        @Override public <T> T getRequestBody(Class<T> type) { return null; }
        @Override public String getPathVariable(String name) { return null; }
        @Override public String getQueryParam(String name) { return null; }
        @Override public String getHeader(String name) { return null; }
        @Override public String getHttpMethod() { return "GET"; }
        @Override public String getRequestPath() { return "/benchmark"; }
        @Override public Map<String, String> getPathVariables() { return Map.of(); }
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
