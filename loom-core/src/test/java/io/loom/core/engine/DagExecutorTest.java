package io.loom.core.engine;

import io.loom.core.builder.BuilderContext;
import io.loom.core.builder.LoomBuilder;
import io.loom.core.exception.BuilderTimeoutException;
import io.loom.core.registry.BuilderFactory;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DagExecutorTest {

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
        Object result = executor.execute(dag, mock(BuilderContext.class));

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
        Object result = executor.execute(dag, mock(BuilderContext.class));

        assertThat(result).isInstanceOf(FinalResult.class);
        assertThat(maxConcurrent.get()).isGreaterThanOrEqualTo(2);
    }
}
