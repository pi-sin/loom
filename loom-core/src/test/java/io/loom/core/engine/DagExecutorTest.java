package io.loom.core.engine;

import io.loom.core.builder.BuilderContext;
import io.loom.core.builder.LoomBuilder;
import io.loom.core.exception.LoomDependencyResolutionException;
import io.loom.core.exception.LoomException;
import io.loom.core.exception.LoomServiceClientException;
import io.loom.core.registry.BuilderFactory;
import io.loom.core.service.ServiceAccessor;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DagExecutorTest {

    // ── Stub BuilderContext that actually stores and resolves dependencies ──

    static class StubBuilderContext implements BuilderContext {
        private final ConcurrentHashMap<Class<?>, Object> resultsByType = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<Class<? extends LoomBuilder<?>>, Object> resultsByBuilder = new ConcurrentHashMap<>();
        private final Map<String, Object> attributes = new ConcurrentHashMap<>();
        private final Map<String, String> pathVars;

        StubBuilderContext() { this(Map.of()); }
        StubBuilderContext(Map<String, String> pathVars) { this.pathVars = pathVars; }

        @Override public <T> T getRequestBody(Class<T> type) { return null; }
        @Override public String getPathVariable(String name) { return pathVars.get(name); }
        @Override public String getQueryParam(String name) { return null; }
        @Override public String getHeader(String name) { return null; }
        @Override public String getHttpMethod() { return "GET"; }
        @Override public String getRequestPath() { return "/test"; }
        @Override public Map<String, String> getPathVariables() { return pathVars; }
        @Override public Map<String, List<String>> getQueryParams() { return Map.of(); }
        @Override public Map<String, List<String>> getHeaders() { return Map.of(); }
        @Override public byte[] getRawRequestBody() { return null; }
        @Override public ServiceAccessor service(String name) { return null; }
        @Override public void setAttribute(String key, Object value) { attributes.put(key, value); }
        @Override @SuppressWarnings("unchecked")
        public <T> T getAttribute(String key, Class<T> type) { return (T) attributes.get(key); }
        @Override public Map<String, Object> getAttributes() { return attributes; }

        private static final Object NULL_SENTINEL = new Object();

        @Override
        public void storeResult(Class<? extends LoomBuilder<?>> builderClass, Class<?> outputType, Object result) {
            Object stored = result != null ? result : NULL_SENTINEL;
            resultsByBuilder.put(builderClass, stored);
            resultsByType.put(outputType, stored);
        }

        @Override @SuppressWarnings("unchecked")
        public <T> T getDependency(Class<T> outputType) {
            Object result = resultsByType.get(outputType);
            if (result == null) throw new LoomDependencyResolutionException(
                    outputType.getSimpleName(),
                    resultsByType.keySet().stream().map(Class::getSimpleName).toList(),
                    resultsByBuilder.keySet().stream().map(Class::getSimpleName).toList());
            return result == NULL_SENTINEL ? null : (T) result;
        }

        @Override @SuppressWarnings("unchecked")
        public <T> T getResultOf(Class<? extends LoomBuilder<T>> builderClass) {
            Object result = resultsByBuilder.get(builderClass);
            if (result == null) throw new LoomDependencyResolutionException(
                    "builder:" + builderClass.getSimpleName(),
                    resultsByType.keySet().stream().map(Class::getSimpleName).toList(),
                    resultsByBuilder.keySet().stream().map(Class::getSimpleName).toList());
            return result == NULL_SENTINEL ? null : (T) result;
        }

        @Override @SuppressWarnings("unchecked")
        public <T> Optional<T> getOptionalDependency(Class<T> outputType) {
            Object value = resultsByType.get(outputType);
            return value == null ? Optional.empty() : Optional.ofNullable(value == NULL_SENTINEL ? null : (T) value);
        }

        @Override @SuppressWarnings("unchecked")
        public <T> Optional<T> getOptionalResultOf(Class<? extends LoomBuilder<T>> builderClass) {
            Object value = resultsByBuilder.get(builderClass);
            return value == null ? Optional.empty() : Optional.ofNullable(value == NULL_SENTINEL ? null : (T) value);
        }
    }

    // ── Simple types + builders ──

    static class FinalResult {
        String value;
        FinalResult(String value) { this.value = value; }
    }

    static class FastBuilder implements LoomBuilder<String> {
        public String build(BuilderContext ctx) { return "fast"; }
    }
    static class SlowBuilder implements LoomBuilder<Integer> {
        public Integer build(BuilderContext ctx) {
            try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            return 42;
        }
    }
    static class AssemblerBuilder implements LoomBuilder<FinalResult> {
        public FinalResult build(BuilderContext ctx) { return new FinalResult("assembled"); }
    }

    // ── Complex 4-level DAG types ──
    //
    //  Level 0:  FetchUser ──────────────────────┐
    //            FetchConfig ─────────────┐      │
    //  Level 1:  EnrichUser (User+Config) ┤      │
    //            FetchOrders (User) ───────┤      │
    //  Level 2:  ScoreUser (EnrichedUser+Orders)──┤
    //  Level 3:  BuildDashboard (User+EnrichedUser+Score+Orders) ← terminal
    //

    record User(String id, String name) {}
    record Config(String region) {}
    record EnrichedUser(String id, String name, String region) {}
    record OrderList(List<String> orderIds) {}
    record UserScore(int score) {}
    record Dashboard(String userId, String userName, String region, int orderCount, int score) {}

    static class FetchUserBuilder implements LoomBuilder<User> {
        public User build(BuilderContext ctx) { return new User("u-42", "Alice"); }
    }
    static class FetchConfigBuilder implements LoomBuilder<Config> {
        public Config build(BuilderContext ctx) { return new Config("us-east-1"); }
    }
    static class EnrichUserBuilder implements LoomBuilder<EnrichedUser> {
        public EnrichedUser build(BuilderContext ctx) {
            User user = ctx.getDependency(User.class);
            Config config = ctx.getDependency(Config.class);
            return new EnrichedUser(user.id(), user.name(), config.region());
        }
    }
    static class FetchOrdersBuilder implements LoomBuilder<OrderList> {
        public OrderList build(BuilderContext ctx) {
            User user = ctx.getDependency(User.class);
            return new OrderList(List.of("ord-1", "ord-2", "ord-3"));
        }
    }
    static class ScoreUserBuilder implements LoomBuilder<UserScore> {
        public UserScore build(BuilderContext ctx) {
            EnrichedUser enriched = ctx.getDependency(EnrichedUser.class);
            OrderList orders = ctx.getDependency(OrderList.class);
            return new UserScore(orders.orderIds().size() * 10);
        }
    }
    static class BuildDashboardBuilder implements LoomBuilder<Dashboard> {
        public Dashboard build(BuilderContext ctx) {
            User user = ctx.getDependency(User.class);
            EnrichedUser enriched = ctx.getDependency(EnrichedUser.class);
            OrderList orders = ctx.getDependency(OrderList.class);
            UserScore score = ctx.getDependency(UserScore.class);
            return new Dashboard(user.id(), enriched.name(), enriched.region(),
                    orders.orderIds().size(), score.score());
        }
    }

    // ── Builder-class disambiguation types ──

    static class TagBuilderA implements LoomBuilder<String> {
        public String build(BuilderContext ctx) { return "tag-A"; }
    }
    static class TagBuilderB implements LoomBuilder<String> {
        public String build(BuilderContext ctx) { return "tag-B"; }
    }
    record MergedTags(String tagA, String tagB) {}
    static class MergeTagsBuilder implements LoomBuilder<MergedTags> {
        public MergedTags build(BuilderContext ctx) {
            String a = ctx.getResultOf(TagBuilderA.class);
            String b = ctx.getResultOf(TagBuilderB.class);
            return new MergedTags(a, b);
        }
    }

    // ── Optional failure types ──

    static class FailingBuilder implements LoomBuilder<String> {
        public String build(BuilderContext ctx) { throw new RuntimeException("boom"); }
    }
    static class ServiceClientFailingBuilder implements LoomBuilder<String> {
        public String build(BuilderContext ctx) {
            throw new LoomServiceClientException("payment-svc", 503, "Bad Gateway");
        }
    }
    record OptionalResult(String primary, Optional<String> secondary) {}
    static class CollectorBuilder implements LoomBuilder<OptionalResult> {
        public OptionalResult build(BuilderContext ctx) {
            String primary = ctx.getDependency(Integer.class).toString();
            Optional<String> secondary = ctx.getOptionalResultOf(FailingBuilder.class);
            return new OptionalResult(primary, secondary);
        }
    }

    // ── Tests ──

    @Test
    void shouldExecuteSimpleDag() {
        BuilderFactory factory = mock(BuilderFactory.class);
        doReturn(new FastBuilder()).when(factory).createBuilderUntyped(FastBuilder.class);
        doReturn(new AssemblerBuilder()).when(factory).createBuilderUntyped(AssemblerBuilder.class);

        Map<Class<? extends LoomBuilder<?>>, DagNode> nodes = new LinkedHashMap<>();
        nodes.put(FastBuilder.class, new DagNode(FastBuilder.class, Set.of(), true, 5000, String.class));
        nodes.put(AssemblerBuilder.class, new DagNode(AssemblerBuilder.class,
                Set.of(FastBuilder.class), true, 5000, FinalResult.class));

        DagNode terminal = nodes.get(AssemblerBuilder.class);
        List<DagNode> topoOrder = List.of(nodes.get(FastBuilder.class), terminal);
        Dag dag = new Dag(nodes, topoOrder, terminal);

        DagExecutor executor = new DagExecutor(factory);
        Object result = executor.execute(dag, new StubBuilderContext());

        assertThat(result).isInstanceOf(FinalResult.class);
    }

    @Test
    void shouldExecuteParallelNodes() {
        AtomicInteger concurrentCount = new AtomicInteger(0);
        AtomicInteger maxConcurrent = new AtomicInteger(0);

        LoomBuilder<String> parallelBuilder1 = ctx -> {
            int current = concurrentCount.incrementAndGet();
            maxConcurrent.updateAndGet(max -> Math.max(max, current));
            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            concurrentCount.decrementAndGet();
            return "p1";
        };

        LoomBuilder<Integer> parallelBuilder2 = ctx -> {
            int current = concurrentCount.incrementAndGet();
            maxConcurrent.updateAndGet(max -> Math.max(max, current));
            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            concurrentCount.decrementAndGet();
            return 42;
        };

        BuilderFactory factory = mock(BuilderFactory.class);
        doReturn(parallelBuilder1).when(factory).createBuilderUntyped(FastBuilder.class);
        doReturn(parallelBuilder2).when(factory).createBuilderUntyped(SlowBuilder.class);
        doReturn(new AssemblerBuilder()).when(factory).createBuilderUntyped(AssemblerBuilder.class);

        Map<Class<? extends LoomBuilder<?>>, DagNode> nodes = new LinkedHashMap<>();
        nodes.put(FastBuilder.class, new DagNode(FastBuilder.class, Set.of(), true, 5000, String.class));
        nodes.put(SlowBuilder.class, new DagNode(SlowBuilder.class, Set.of(), true, 5000, Integer.class));
        nodes.put(AssemblerBuilder.class, new DagNode(AssemblerBuilder.class,
                Set.of(FastBuilder.class, SlowBuilder.class), true, 5000, FinalResult.class));

        DagNode terminal = nodes.get(AssemblerBuilder.class);
        List<DagNode> topoOrder = List.of(
                nodes.get(FastBuilder.class), nodes.get(SlowBuilder.class), terminal);
        Dag dag = new Dag(nodes, topoOrder, terminal);

        DagExecutor executor = new DagExecutor(factory);
        Object result = executor.execute(dag, new StubBuilderContext());

        assertThat(result).isInstanceOf(FinalResult.class);
        assertThat(maxConcurrent.get()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void shouldPassDependenciesAcross4Levels() {
        BuilderFactory factory = mock(BuilderFactory.class);
        doReturn(new FetchUserBuilder()).when(factory).createBuilderUntyped(FetchUserBuilder.class);
        doReturn(new FetchConfigBuilder()).when(factory).createBuilderUntyped(FetchConfigBuilder.class);
        doReturn(new EnrichUserBuilder()).when(factory).createBuilderUntyped(EnrichUserBuilder.class);
        doReturn(new FetchOrdersBuilder()).when(factory).createBuilderUntyped(FetchOrdersBuilder.class);
        doReturn(new ScoreUserBuilder()).when(factory).createBuilderUntyped(ScoreUserBuilder.class);
        doReturn(new BuildDashboardBuilder()).when(factory).createBuilderUntyped(BuildDashboardBuilder.class);

        Map<Class<? extends LoomBuilder<?>>, DagNode> nodes = new LinkedHashMap<>();
        nodes.put(FetchUserBuilder.class,
                new DagNode(FetchUserBuilder.class, Set.of(), true, 5000, User.class));
        nodes.put(FetchConfigBuilder.class,
                new DagNode(FetchConfigBuilder.class, Set.of(), true, 5000, Config.class));
        nodes.put(EnrichUserBuilder.class,
                new DagNode(EnrichUserBuilder.class,
                        Set.of(FetchUserBuilder.class, FetchConfigBuilder.class), true, 5000, EnrichedUser.class));
        nodes.put(FetchOrdersBuilder.class,
                new DagNode(FetchOrdersBuilder.class,
                        Set.of(FetchUserBuilder.class), true, 5000, OrderList.class));
        nodes.put(ScoreUserBuilder.class,
                new DagNode(ScoreUserBuilder.class,
                        Set.of(EnrichUserBuilder.class, FetchOrdersBuilder.class), true, 5000, UserScore.class));
        nodes.put(BuildDashboardBuilder.class,
                new DagNode(BuildDashboardBuilder.class,
                        Set.of(FetchUserBuilder.class, EnrichUserBuilder.class,
                                FetchOrdersBuilder.class, ScoreUserBuilder.class),
                        true, 5000, Dashboard.class));

        DagNode terminal = nodes.get(BuildDashboardBuilder.class);
        List<DagNode> topoOrder = List.of(
                nodes.get(FetchUserBuilder.class),
                nodes.get(FetchConfigBuilder.class),
                nodes.get(EnrichUserBuilder.class),
                nodes.get(FetchOrdersBuilder.class),
                nodes.get(ScoreUserBuilder.class),
                terminal
        );
        Dag dag = new Dag(nodes, topoOrder, terminal);

        DagExecutor executor = new DagExecutor(factory);
        Object result = executor.execute(dag, new StubBuilderContext());

        assertThat(result).isInstanceOf(Dashboard.class);
        Dashboard dashboard = (Dashboard) result;
        assertThat(dashboard.userId()).isEqualTo("u-42");
        assertThat(dashboard.userName()).isEqualTo("Alice");
        assertThat(dashboard.region()).isEqualTo("us-east-1");
        assertThat(dashboard.orderCount()).isEqualTo(3);
        assertThat(dashboard.score()).isEqualTo(30);
    }

    @Test
    void shouldResolveByBuilderClass() {
        BuilderFactory factory = mock(BuilderFactory.class);
        doReturn(new TagBuilderA()).when(factory).createBuilderUntyped(TagBuilderA.class);
        doReturn(new TagBuilderB()).when(factory).createBuilderUntyped(TagBuilderB.class);
        doReturn(new MergeTagsBuilder()).when(factory).createBuilderUntyped(MergeTagsBuilder.class);

        Map<Class<? extends LoomBuilder<?>>, DagNode> nodes = new LinkedHashMap<>();
        nodes.put(TagBuilderA.class, new DagNode(TagBuilderA.class, Set.of(), true, 5000, String.class));
        nodes.put(TagBuilderB.class, new DagNode(TagBuilderB.class, Set.of(), true, 5000, String.class));
        nodes.put(MergeTagsBuilder.class, new DagNode(MergeTagsBuilder.class,
                Set.of(TagBuilderA.class, TagBuilderB.class), true, 5000, MergedTags.class));

        DagNode terminal = nodes.get(MergeTagsBuilder.class);
        List<DagNode> topoOrder = List.of(
                nodes.get(TagBuilderA.class), nodes.get(TagBuilderB.class), terminal);
        Dag dag = new Dag(nodes, topoOrder, terminal);

        DagExecutor executor = new DagExecutor(factory);
        Object result = executor.execute(dag, new StubBuilderContext());

        assertThat(result).isInstanceOf(MergedTags.class);
        MergedTags merged = (MergedTags) result;
        assertThat(merged.tagA()).isEqualTo("tag-A");
        assertThat(merged.tagB()).isEqualTo("tag-B");
    }

    @Test
    void shouldHandleOptionalFailedNode() {
        BuilderFactory factory = mock(BuilderFactory.class);
        doReturn(new SlowBuilder()).when(factory).createBuilderUntyped(SlowBuilder.class);
        doReturn(new FailingBuilder()).when(factory).createBuilderUntyped(FailingBuilder.class);
        doReturn(new CollectorBuilder()).when(factory).createBuilderUntyped(CollectorBuilder.class);

        Map<Class<? extends LoomBuilder<?>>, DagNode> nodes = new LinkedHashMap<>();
        nodes.put(SlowBuilder.class, new DagNode(SlowBuilder.class, Set.of(), true, 5000, Integer.class));
        nodes.put(FailingBuilder.class, new DagNode(FailingBuilder.class, Set.of(), false, 5000, String.class));
        nodes.put(CollectorBuilder.class, new DagNode(CollectorBuilder.class,
                Set.of(SlowBuilder.class, FailingBuilder.class), true, 5000, OptionalResult.class));

        DagNode terminal = nodes.get(CollectorBuilder.class);
        List<DagNode> topoOrder = List.of(
                nodes.get(SlowBuilder.class), nodes.get(FailingBuilder.class), terminal);
        Dag dag = new Dag(nodes, topoOrder, terminal);

        DagExecutor executor = new DagExecutor(factory);
        Object result = executor.execute(dag, new StubBuilderContext());

        assertThat(result).isInstanceOf(OptionalResult.class);
        OptionalResult opt = (OptionalResult) result;
        assertThat(opt.primary()).isEqualTo("42");
        assertThat(opt.secondary()).isEmpty();
    }

    @Test
    void shouldReportAvailableDependenciesOnFailure() {
        // A builder that tries to get a dependency that doesn't exist
        LoomBuilder<String> badBuilder = ctx -> {
            ctx.getDependency(Dashboard.class); // not available
            return "unreachable";
        };

        BuilderFactory factory = mock(BuilderFactory.class);
        doReturn(new FastBuilder()).when(factory).createBuilderUntyped(FastBuilder.class);
        doReturn(badBuilder).when(factory).createBuilderUntyped(AssemblerBuilder.class);

        Map<Class<? extends LoomBuilder<?>>, DagNode> nodes = new LinkedHashMap<>();
        nodes.put(FastBuilder.class, new DagNode(FastBuilder.class, Set.of(), true, 5000, String.class));
        nodes.put(AssemblerBuilder.class, new DagNode(AssemblerBuilder.class,
                Set.of(FastBuilder.class), true, 5000, FinalResult.class));

        DagNode terminal = nodes.get(AssemblerBuilder.class);
        List<DagNode> topoOrder = List.of(nodes.get(FastBuilder.class), terminal);
        Dag dag = new Dag(nodes, topoOrder, terminal);

        DagExecutor executor = new DagExecutor(factory);

        assertThatThrownBy(() -> executor.execute(dag, new StubBuilderContext()))
                .rootCause()
                .isInstanceOf(LoomDependencyResolutionException.class)
                .satisfies(root -> {
                    LoomDependencyResolutionException dre = (LoomDependencyResolutionException) root;
                    assertThat(dre.getRequestedType()).isEqualTo("Dashboard");
                    assertThat(dre.getAvailableTypes()).contains("String");
                    assertThat(dre.getCompletedBuilders()).contains("FastBuilder");
                    assertThat(dre.getMessage()).contains("Available output types:");
                    assertThat(dre.getMessage()).contains("Completed builders:");
                });
    }

    // ── Null builder output types ──

    static class NullReturningBuilder implements LoomBuilder<String> {
        public String build(BuilderContext ctx) { return null; }
    }
    static class NullConsumerBuilder implements LoomBuilder<FinalResult> {
        public FinalResult build(BuilderContext ctx) {
            String value = ctx.getDependency(String.class);
            return new FinalResult(value == null ? "was-null" : value);
        }
    }

    @Test
    void shouldHandleBuilderReturningNull() {
        BuilderFactory factory = mock(BuilderFactory.class);
        doReturn(new NullReturningBuilder()).when(factory).createBuilderUntyped(NullReturningBuilder.class);
        doReturn(new NullConsumerBuilder()).when(factory).createBuilderUntyped(NullConsumerBuilder.class);

        Map<Class<? extends LoomBuilder<?>>, DagNode> nodes = new LinkedHashMap<>();
        nodes.put(NullReturningBuilder.class, new DagNode(NullReturningBuilder.class, Set.of(), true, 5000, String.class));
        nodes.put(NullConsumerBuilder.class, new DagNode(NullConsumerBuilder.class,
                Set.of(NullReturningBuilder.class), true, 5000, FinalResult.class));

        DagNode terminal = nodes.get(NullConsumerBuilder.class);
        List<DagNode> topoOrder = List.of(nodes.get(NullReturningBuilder.class), terminal);
        Dag dag = new Dag(nodes, topoOrder, terminal);

        DagExecutor executor = new DagExecutor(factory);
        Object result = executor.execute(dag, new StubBuilderContext());

        assertThat(result).isInstanceOf(FinalResult.class);
        assertThat(((FinalResult) result).value).isEqualTo("was-null");
    }

    @Test
    void optionalFailedNodeStoresUnwrappedCause() {
        // Optional node fails with LoomServiceClientException — verify the cause
        // stored in BuilderResult is the original exception, not CompletionException
        BuilderFactory factory = mock(BuilderFactory.class);
        doReturn(new SlowBuilder()).when(factory).createBuilderUntyped(SlowBuilder.class);
        doReturn(new ServiceClientFailingBuilder()).when(factory).createBuilderUntyped(ServiceClientFailingBuilder.class);

        // Collector that inspects both required and optional results
        LoomBuilder<String> inspectorBuilder = ctx -> {
            Integer required = ctx.getDependency(Integer.class);
            Optional<String> optional = ctx.getOptionalResultOf(ServiceClientFailingBuilder.class);
            return "required=" + required + ",optional=" + optional.orElse("absent");
        };
        doReturn(inspectorBuilder).when(factory).createBuilderUntyped(AssemblerBuilder.class);

        Map<Class<? extends LoomBuilder<?>>, DagNode> nodes = new LinkedHashMap<>();
        nodes.put(SlowBuilder.class, new DagNode(SlowBuilder.class, Set.of(), true, 5000, Integer.class));
        nodes.put(ServiceClientFailingBuilder.class,
                new DagNode(ServiceClientFailingBuilder.class, Set.of(), false, 5000, String.class));
        nodes.put(AssemblerBuilder.class, new DagNode(AssemblerBuilder.class,
                Set.of(SlowBuilder.class, ServiceClientFailingBuilder.class), true, 5000, FinalResult.class));

        DagNode terminal = nodes.get(AssemblerBuilder.class);
        List<DagNode> topoOrder = List.of(
                nodes.get(SlowBuilder.class), nodes.get(ServiceClientFailingBuilder.class), terminal);
        Dag dag = new Dag(nodes, topoOrder, terminal);

        DagExecutor executor = new DagExecutor(factory);
        Object result = executor.execute(dag, new StubBuilderContext());

        // The optional node failed, so its result is absent (not CompletionException)
        assertThat(result).isInstanceOf(String.class);
        assertThat((String) result).isEqualTo("required=42,optional=absent");
    }
}
