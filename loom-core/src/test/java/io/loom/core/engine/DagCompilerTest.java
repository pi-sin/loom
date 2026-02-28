package io.loom.core.engine;

import io.loom.core.annotation.LoomApi;
import io.loom.core.annotation.LoomGraph;
import io.loom.core.annotation.Node;
import io.loom.core.builder.BuilderContext;
import io.loom.core.builder.LoomBuilder;
import io.loom.core.exception.LoomException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class DagCompilerTest {

    private final DagCompiler compiler = new DagCompiler();

    static class OutputA {}
    static class OutputFinal {}

    static class TestBuilderA implements LoomBuilder<OutputA> {
        public OutputA build(BuilderContext ctx) { return new OutputA(); }
    }
    static class TestTerminal implements LoomBuilder<OutputFinal> {
        public OutputFinal build(BuilderContext ctx) { return new OutputFinal(); }
    }

    @LoomApi(method = "GET", path = "/test", response = OutputFinal.class)
    @LoomGraph({
        @Node(builder = TestBuilderA.class),
        @Node(builder = TestTerminal.class, dependsOn = TestBuilderA.class)
    })
    static class ValidApiClass {}

    @LoomApi(method = "GET", path = "/test", response = OutputFinal.class)
    static class MissingGraphClass {}

    @Test
    void shouldCompileFromAnnotations() {
        Dag dag = compiler.compile(ValidApiClass.class);

        assertThat(dag.size()).isEqualTo(2);
        assertThat(dag.getTerminalNode().builderClass()).isEqualTo(TestTerminal.class);
        assertThat(dag.topologicalOrder()).hasSize(2);
    }

    @Test
    void shouldFailOnMissingGraph() {
        assertThatThrownBy(() -> compiler.compile(MissingGraphClass.class))
                .isInstanceOf(LoomException.class)
                .hasMessageContaining("@LoomGraph");
    }

    @Test
    void shouldResolveOutputType() {
        Class<?> outputType = DagCompiler.resolveOutputType(TestBuilderA.class);
        assertThat(outputType).isEqualTo(OutputA.class);
    }

    // ── Intermediate interface resolution ──

    static class PricingResult {}

    interface PricingBuilder extends LoomBuilder<PricingResult> {}

    static class ConcretePricingBuilder implements PricingBuilder {
        public PricingResult build(BuilderContext ctx) { return new PricingResult(); }
    }

    @Test
    void shouldResolveOutputTypeThroughIntermediateInterface() {
        Class<?> outputType = DagCompiler.resolveOutputType(ConcretePricingBuilder.class);
        assertThat(outputType).isEqualTo(PricingResult.class);
    }

    // ── Index verification ──

    static class OutputB {}

    static class TestBuilderB implements LoomBuilder<OutputB> {
        public OutputB build(BuilderContext ctx) { return new OutputB(); }
    }

    @LoomApi(method = "GET", path = "/indexed", response = OutputFinal.class)
    @LoomGraph({
        @Node(builder = TestBuilderA.class),
        @Node(builder = TestBuilderB.class),
        @Node(builder = TestTerminal.class, dependsOn = {TestBuilderA.class, TestBuilderB.class})
    })
    static class IndexedApiClass {}

    @Test
    void shouldAssignCorrectIndicesAndMaps() {
        Dag dag = compiler.compile(IndexedApiClass.class);

        assertThat(dag.size()).isEqualTo(3);

        // All nodes have indices in [0, nodeCount)
        for (DagNode node : dag.topologicalOrder()) {
            assertThat(node.index()).isBetween(0, dag.size() - 1);
        }

        // Terminal's dependencyIndices point to the correct builders
        DagNode terminal = dag.getTerminalNode();
        assertThat(terminal.builderClass()).isEqualTo(TestTerminal.class);
        assertThat(terminal.dependencyIndices()).hasSize(2);

        int builderAIndex = dag.builderIndexMap().get(TestBuilderA.class);
        int builderBIndex = dag.builderIndexMap().get(TestBuilderB.class);
        assertThat(terminal.dependencyIndices()).containsExactlyInAnyOrder(builderAIndex, builderBIndex);

        // typeIndexMap contains all expected entries
        assertThat(dag.typeIndexMap()).containsKeys(OutputA.class, OutputB.class, OutputFinal.class);
        assertThat(dag.typeIndexMap()).hasSize(3);

        // builderIndexMap contains all expected entries
        assertThat(dag.builderIndexMap()).containsKeys(TestBuilderA.class, TestBuilderB.class, TestTerminal.class);
        assertThat(dag.builderIndexMap()).hasSize(3);

        // Index consistency: typeIndexMap and builderIndexMap agree on index for each node
        for (DagNode node : dag.topologicalOrder()) {
            int byType = dag.typeIndexMap().get(node.outputType());
            int byBuilder = dag.builderIndexMap().get(node.builderClass());
            assertThat(byType).isEqualTo(node.index());
            assertThat(byBuilder).isEqualTo(node.index());
        }
    }
}
