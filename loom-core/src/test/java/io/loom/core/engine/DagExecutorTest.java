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
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DagExecutorTest {

    // ── Stub BuilderContext that supports array-based storage ──

    static class StubBuilderContext implements BuilderContext {
        private final Map<String, Object> attributes = new HashMap<>();
        private final Map<String, String> pathVars;

        // Array-based result storage
        private Object[] results;
        private Map<Class<?>, Integer> typeIndexMap;
        private Map<Class<? extends LoomBuilder<?>>, Integer> builderIndexMap;
        private static final Object NULL_SENTINEL = new Object();

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
            if (index != null) {
                results[index] = stored;
            }
        }

        @Override @SuppressWarnings("unchecked")
        public <T> T getDependency(Class<T> outputType) {
            Integer index = typeIndexMap.get(outputType);
            if (index == null || results[index] == null) {
                throw new LoomDependencyResolutionException(
                        outputType.getSimpleName(),
                        availableTypeNames(),
                        availableBuilderNames());
            }
            Object result = results[index];
            return result == NULL_SENTINEL ? null : (T) result;
        }

        @Override @SuppressWarnings("unchecked")
        public <T> T getResultOf(Class<? extends LoomBuilder<T>> builderClass) {
            Integer index = builderIndexMap.get(builderClass);
            if (index == null || results[index] == null) {
                throw new LoomDependencyResolutionException(
                        "builder:" + builderClass.getSimpleName(),
                        availableTypeNames(),
                        availableBuilderNames());
            }
            Object result = results[index];
            return result == NULL_SENTINEL ? null : (T) result;
        }

        @Override @SuppressWarnings("unchecked")
        public <T> Optional<T> getOptionalDependency(Class<T> outputType) {
            Integer index = typeIndexMap != null ? typeIndexMap.get(outputType) : null;
            if (index == null) return Optional.empty();
            Object value = results[index];
            return value == null ? Optional.empty() : Optional.ofNullable(value == NULL_SENTINEL ? null : (T) value);
        }

        @Override @SuppressWarnings("unchecked")
        public <T> Optional<T> getOptionalResultOf(Class<? extends LoomBuilder<T>> builderClass) {
            Integer index = builderIndexMap != null ? builderIndexMap.get(builderClass) : null;
            if (index == null) return Optional.empty();
            Object value = results[index];
            return value == null ? Optional.empty() : Optional.ofNullable(value == NULL_SENTINEL ? null : (T) value);
        }

        private List<String> availableTypeNames() {
            if (typeIndexMap == null) return List.of();
            List<String> names = new ArrayList<>();
            for (var entry : typeIndexMap.entrySet()) {
                if (results[entry.getValue()] != null) {
                    names.add(entry.getKey().getSimpleName());
                }
            }
            return names;
        }

        private List<String> availableBuilderNames() {
            if (builderIndexMap == null) return List.of();
            List<String> names = new ArrayList<>();
            for (var entry : builderIndexMap.entrySet()) {
                if (results[entry.getValue()] != null) {
                    names.add(entry.getKey().getSimpleName());
                }
            }
            return names;
        }
    }

    // ── Helper: build an indexed Dag from a list of DagNode in topological order ──

    static Dag buildDag(List<DagNode> topoOrder, DagNode terminal) {
        Map<Class<? extends LoomBuilder<?>>, Integer> builderToIndex = new HashMap<>();
        List<DagNode> indexedOrder = new ArrayList<>(topoOrder.size());

        for (int i = 0; i < topoOrder.size(); i++) {
            DagNode orig = topoOrder.get(i);
            builderToIndex.put(orig.builderClass(), i);

            int[] depIndices = new int[orig.dependsOn().size()];
            int di = 0;
            for (var dep : orig.dependsOn()) {
                depIndices[di++] = builderToIndex.get(dep);
            }

            indexedOrder.add(new DagNode(orig.builderClass(), orig.dependsOn(),
                    orig.required(), orig.timeoutMs(), orig.outputType(), i, depIndices));
        }

        Map<Class<? extends LoomBuilder<?>>, DagNode> nodesMap = new LinkedHashMap<>();
        Map<Class<?>, Integer> typeIndexMap = new HashMap<>();
        Map<Class<? extends LoomBuilder<?>>, Integer> builderIndexMap = new HashMap<>();

        for (DagNode node : indexedOrder) {
            nodesMap.put(node.builderClass(), node);
            typeIndexMap.put(node.outputType(), node.index());
            builderIndexMap.put(node.builderClass(), node.index());
        }

        DagNode indexedTerminal = nodesMap.get(terminal.builderClass());
        return new Dag(nodesMap, indexedOrder, indexedTerminal, typeIndexMap, builderIndexMap);
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

        DagNode fast = new DagNode(FastBuilder.class, Set.of(), true, 5000, String.class);
        DagNode assembler = new DagNode(AssemblerBuilder.class,
                Set.of(FastBuilder.class), true, 5000, FinalResult.class);

        Dag dag = buildDag(List.of(fast, assembler), assembler);

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

        DagNode fast = new DagNode(FastBuilder.class, Set.of(), true, 5000, String.class);
        DagNode slow = new DagNode(SlowBuilder.class, Set.of(), true, 5000, Integer.class);
        DagNode assembler = new DagNode(AssemblerBuilder.class,
                Set.of(FastBuilder.class, SlowBuilder.class), true, 5000, FinalResult.class);

        Dag dag = buildDag(List.of(fast, slow, assembler), assembler);

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

        DagNode fetchUser = new DagNode(FetchUserBuilder.class, Set.of(), true, 5000, User.class);
        DagNode fetchConfig = new DagNode(FetchConfigBuilder.class, Set.of(), true, 5000, Config.class);
        DagNode enrichUser = new DagNode(EnrichUserBuilder.class,
                Set.of(FetchUserBuilder.class, FetchConfigBuilder.class), true, 5000, EnrichedUser.class);
        DagNode fetchOrders = new DagNode(FetchOrdersBuilder.class,
                Set.of(FetchUserBuilder.class), true, 5000, OrderList.class);
        DagNode scoreUser = new DagNode(ScoreUserBuilder.class,
                Set.of(EnrichUserBuilder.class, FetchOrdersBuilder.class), true, 5000, UserScore.class);
        DagNode buildDashboard = new DagNode(BuildDashboardBuilder.class,
                Set.of(FetchUserBuilder.class, EnrichUserBuilder.class,
                        FetchOrdersBuilder.class, ScoreUserBuilder.class),
                true, 5000, Dashboard.class);

        Dag dag = buildDag(
                List.of(fetchUser, fetchConfig, enrichUser, fetchOrders, scoreUser, buildDashboard),
                buildDashboard);

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

        DagNode tagA = new DagNode(TagBuilderA.class, Set.of(), true, 5000, String.class);
        DagNode tagB = new DagNode(TagBuilderB.class, Set.of(), true, 5000, String.class);
        DagNode merge = new DagNode(MergeTagsBuilder.class,
                Set.of(TagBuilderA.class, TagBuilderB.class), true, 5000, MergedTags.class);

        Dag dag = buildDag(List.of(tagA, tagB, merge), merge);

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

        DagNode slow = new DagNode(SlowBuilder.class, Set.of(), true, 5000, Integer.class);
        DagNode failing = new DagNode(FailingBuilder.class, Set.of(), false, 5000, String.class);
        DagNode collector = new DagNode(CollectorBuilder.class,
                Set.of(SlowBuilder.class, FailingBuilder.class), true, 5000, OptionalResult.class);

        Dag dag = buildDag(List.of(slow, failing, collector), collector);

        DagExecutor executor = new DagExecutor(factory);
        Object result = executor.execute(dag, new StubBuilderContext());

        assertThat(result).isInstanceOf(OptionalResult.class);
        OptionalResult opt = (OptionalResult) result;
        assertThat(opt.primary()).isEqualTo("42");
        assertThat(opt.secondary()).isEmpty();
    }

    @Test
    void shouldExecuteSingleNodeDag() {
        BuilderFactory factory = mock(BuilderFactory.class);
        doReturn(new AssemblerBuilder()).when(factory).createBuilderUntyped(AssemblerBuilder.class);

        DagNode assembler = new DagNode(AssemblerBuilder.class, Set.of(), true, 5000, FinalResult.class);
        Dag dag = buildDag(List.of(assembler), assembler);

        DagExecutor executor = new DagExecutor(factory);
        Object result = executor.execute(dag, new StubBuilderContext());

        assertThat(result).isInstanceOf(FinalResult.class);
        assertThat(((FinalResult) result).value).isEqualTo("assembled");
    }

    // ── Diamond dependency types ──

    record DiamondA(String value) {}
    record DiamondB(int value) {}
    record DiamondC(String combined) {}
    record DiamondResult(String a, int b, String c) {}

    static class DiamondABuilder implements LoomBuilder<DiamondA> {
        public DiamondA build(BuilderContext ctx) { return new DiamondA("alpha"); }
    }
    static class DiamondBBuilder implements LoomBuilder<DiamondB> {
        public DiamondB build(BuilderContext ctx) { return new DiamondB(99); }
    }
    static class DiamondCBuilder implements LoomBuilder<DiamondC> {
        public DiamondC build(BuilderContext ctx) {
            DiamondA a = ctx.getDependency(DiamondA.class);
            DiamondB b = ctx.getDependency(DiamondB.class);
            return new DiamondC(a.value() + "-" + b.value());
        }
    }
    static class DiamondTerminalBuilder implements LoomBuilder<DiamondResult> {
        public DiamondResult build(BuilderContext ctx) {
            DiamondA a = ctx.getDependency(DiamondA.class);
            DiamondB b = ctx.getDependency(DiamondB.class);
            DiamondC c = ctx.getDependency(DiamondC.class);
            return new DiamondResult(a.value(), b.value(), c.combined());
        }
    }

    @Test
    void shouldExecuteDiamondDependencyPattern() {
        BuilderFactory factory = mock(BuilderFactory.class);
        doReturn(new DiamondABuilder()).when(factory).createBuilderUntyped(DiamondABuilder.class);
        doReturn(new DiamondBBuilder()).when(factory).createBuilderUntyped(DiamondBBuilder.class);
        doReturn(new DiamondCBuilder()).when(factory).createBuilderUntyped(DiamondCBuilder.class);
        doReturn(new DiamondTerminalBuilder()).when(factory).createBuilderUntyped(DiamondTerminalBuilder.class);

        DagNode nodeA = new DagNode(DiamondABuilder.class, Set.of(), true, 5000, DiamondA.class);
        DagNode nodeB = new DagNode(DiamondBBuilder.class, Set.of(), true, 5000, DiamondB.class);
        DagNode nodeC = new DagNode(DiamondCBuilder.class,
                Set.of(DiamondABuilder.class, DiamondBBuilder.class), true, 5000, DiamondC.class);
        DagNode terminal = new DagNode(DiamondTerminalBuilder.class,
                Set.of(DiamondABuilder.class, DiamondBBuilder.class, DiamondCBuilder.class),
                true, 5000, DiamondResult.class);

        Dag dag = buildDag(List.of(nodeA, nodeB, nodeC, terminal), terminal);

        DagExecutor executor = new DagExecutor(factory);
        Object result = executor.execute(dag, new StubBuilderContext());

        assertThat(result).isInstanceOf(DiamondResult.class);
        DiamondResult diamond = (DiamondResult) result;
        assertThat(diamond.a()).isEqualTo("alpha");
        assertThat(diamond.b()).isEqualTo(99);
        assertThat(diamond.c()).isEqualTo("alpha-99");
    }

    @Test
    void shouldReportAvailableDependenciesOnFailure() {
        LoomBuilder<String> badBuilder = ctx -> {
            ctx.getDependency(Dashboard.class);
            return "unreachable";
        };

        BuilderFactory factory = mock(BuilderFactory.class);
        doReturn(new FastBuilder()).when(factory).createBuilderUntyped(FastBuilder.class);
        doReturn(badBuilder).when(factory).createBuilderUntyped(AssemblerBuilder.class);

        DagNode fast = new DagNode(FastBuilder.class, Set.of(), true, 5000, String.class);
        DagNode assembler = new DagNode(AssemblerBuilder.class,
                Set.of(FastBuilder.class), true, 5000, FinalResult.class);

        Dag dag = buildDag(List.of(fast, assembler), assembler);

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

        DagNode nullReturning = new DagNode(NullReturningBuilder.class, Set.of(), true, 5000, String.class);
        DagNode nullConsumer = new DagNode(NullConsumerBuilder.class,
                Set.of(NullReturningBuilder.class), true, 5000, FinalResult.class);

        Dag dag = buildDag(List.of(nullReturning, nullConsumer), nullConsumer);

        DagExecutor executor = new DagExecutor(factory);
        Object result = executor.execute(dag, new StubBuilderContext());

        assertThat(result).isInstanceOf(FinalResult.class);
        assertThat(((FinalResult) result).value).isEqualTo("was-null");
    }

    @Test
    void optionalFailedNodeStoresUnwrappedCause() {
        BuilderFactory factory = mock(BuilderFactory.class);
        doReturn(new SlowBuilder()).when(factory).createBuilderUntyped(SlowBuilder.class);
        doReturn(new ServiceClientFailingBuilder()).when(factory).createBuilderUntyped(ServiceClientFailingBuilder.class);

        LoomBuilder<String> inspectorBuilder = ctx -> {
            Integer required = ctx.getDependency(Integer.class);
            Optional<String> optional = ctx.getOptionalResultOf(ServiceClientFailingBuilder.class);
            return "required=" + required + ",optional=" + optional.orElse("absent");
        };
        doReturn(inspectorBuilder).when(factory).createBuilderUntyped(AssemblerBuilder.class);

        DagNode slow = new DagNode(SlowBuilder.class, Set.of(), true, 5000, Integer.class);
        DagNode svcFailing = new DagNode(ServiceClientFailingBuilder.class, Set.of(), false, 5000, String.class);
        DagNode assembler = new DagNode(AssemblerBuilder.class,
                Set.of(SlowBuilder.class, ServiceClientFailingBuilder.class), true, 5000, FinalResult.class);

        Dag dag = buildDag(List.of(slow, svcFailing, assembler), assembler);

        DagExecutor executor = new DagExecutor(factory);
        Object result = executor.execute(dag, new StubBuilderContext());

        assertThat(result).isInstanceOf(String.class);
        assertThat((String) result).isEqualTo("required=42,optional=absent");
    }
}
