package io.loom.core.engine;

import io.loom.core.builder.BuilderContext;
import io.loom.core.builder.LoomBuilder;
import io.loom.core.exception.CycleDetectedException;
import io.loom.core.exception.LoomException;
import io.loom.core.exception.UnknownDependencyException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

class DagValidatorTest {

    private DagValidator validator;

    // Test builder stubs
    static class OutputA {}
    static class OutputB {}
    static class OutputC {}
    static class FinalOutput {}

    static class BuilderA implements LoomBuilder<OutputA> {
        public OutputA build(BuilderContext ctx) { return new OutputA(); }
    }
    static class BuilderB implements LoomBuilder<OutputB> {
        public OutputB build(BuilderContext ctx) { return new OutputB(); }
    }
    static class BuilderC implements LoomBuilder<OutputC> {
        public OutputC build(BuilderContext ctx) { return new OutputC(); }
    }
    static class TerminalBuilder implements LoomBuilder<FinalOutput> {
        public FinalOutput build(BuilderContext ctx) { return new FinalOutput(); }
    }

    @BeforeEach
    void setUp() {
        validator = new DagValidator();
    }

    @Test
    void shouldValidateLinearDag() {
        Map<Class<? extends LoomBuilder<?>>, DagNode> nodes = new LinkedHashMap<>();
        nodes.put(BuilderA.class, new DagNode(BuilderA.class, Set.of(), true, 5000, OutputA.class));
        nodes.put(BuilderB.class, new DagNode(BuilderB.class, Set.of(BuilderA.class), true, 5000, OutputB.class));
        nodes.put(TerminalBuilder.class, new DagNode(TerminalBuilder.class, Set.of(BuilderB.class), true, 5000, FinalOutput.class));

        DagValidator.ValidationResult result = validator.validate(nodes, FinalOutput.class);

        assertThat(result.topologicalOrder()).hasSize(3);
        assertThat(result.terminalNode().builderClass()).isEqualTo(TerminalBuilder.class);
    }

    @Test
    void shouldDetectCycle() {
        Map<Class<? extends LoomBuilder<?>>, DagNode> nodes = new LinkedHashMap<>();
        nodes.put(BuilderA.class, new DagNode(BuilderA.class, Set.of(BuilderB.class), true, 5000, OutputA.class));
        nodes.put(BuilderB.class, new DagNode(BuilderB.class, Set.of(BuilderA.class), true, 5000, OutputB.class));

        assertThatThrownBy(() -> validator.validate(nodes, OutputA.class))
                .isInstanceOf(CycleDetectedException.class);
    }

    @Test
    void shouldDetectUnknownDependency() {
        Map<Class<? extends LoomBuilder<?>>, DagNode> nodes = new LinkedHashMap<>();
        nodes.put(BuilderA.class, new DagNode(BuilderA.class, Set.of(BuilderC.class), true, 5000, OutputA.class));

        assertThatThrownBy(() -> validator.validate(nodes, OutputA.class))
                .isInstanceOf(UnknownDependencyException.class);
    }

    @Test
    void shouldAutoDetectTerminalNode() {
        Map<Class<? extends LoomBuilder<?>>, DagNode> nodes = new LinkedHashMap<>();
        nodes.put(BuilderA.class, new DagNode(BuilderA.class, Set.of(), true, 5000, OutputA.class));
        nodes.put(TerminalBuilder.class, new DagNode(TerminalBuilder.class, Set.of(BuilderA.class), true, 5000, FinalOutput.class));

        DagValidator.ValidationResult result = validator.validate(nodes, FinalOutput.class);
        assertThat(result.terminalNode().builderClass()).isEqualTo(TerminalBuilder.class);
    }

    @Test
    void shouldFailWhenNoTerminalNodeFound() {
        Map<Class<? extends LoomBuilder<?>>, DagNode> nodes = new LinkedHashMap<>();
        nodes.put(BuilderA.class, new DagNode(BuilderA.class, Set.of(), true, 5000, OutputA.class));

        assertThatThrownBy(() -> validator.validate(nodes, FinalOutput.class))
                .isInstanceOf(LoomException.class)
                .hasMessageContaining("No terminal node found");
    }

    @Test
    void shouldHandleParallelNodes() {
        Map<Class<? extends LoomBuilder<?>>, DagNode> nodes = new LinkedHashMap<>();
        nodes.put(BuilderA.class, new DagNode(BuilderA.class, Set.of(), true, 5000, OutputA.class));
        nodes.put(BuilderB.class, new DagNode(BuilderB.class, Set.of(), true, 5000, OutputB.class));
        nodes.put(TerminalBuilder.class, new DagNode(TerminalBuilder.class,
                Set.of(BuilderA.class, BuilderB.class), true, 5000, FinalOutput.class));

        DagValidator.ValidationResult result = validator.validate(nodes, FinalOutput.class);
        assertThat(result.topologicalOrder()).hasSize(3);

        // Terminal should be last
        assertThat(result.topologicalOrder().get(2).builderClass()).isEqualTo(TerminalBuilder.class);
    }
}
