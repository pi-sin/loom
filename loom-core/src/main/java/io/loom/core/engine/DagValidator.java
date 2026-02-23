package io.loom.core.engine;

import io.loom.core.builder.LoomBuilder;
import io.loom.core.exception.CycleDetectedException;
import io.loom.core.exception.LoomException;
import io.loom.core.exception.UnknownDependencyException;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Slf4j
public class DagValidator {

    public record ValidationResult(
        List<DagNode> topologicalOrder,
        DagNode terminalNode
    ) {}

    public ValidationResult validate(Map<Class<? extends LoomBuilder<?>>, DagNode> nodes,
                                     Class<?> responseType) {
        validateDependenciesExist(nodes);
        List<DagNode> topoOrder = topologicalSort(nodes);
        DagNode terminal = detectTerminalNode(nodes, responseType);
        log.debug("[Loom] DAG validated: {} nodes, terminal='{}'", nodes.size(), terminal.name());
        return new ValidationResult(topoOrder, terminal);
    }

    private void validateDependenciesExist(Map<Class<? extends LoomBuilder<?>>, DagNode> nodes) {
        for (DagNode node : nodes.values()) {
            for (Class<? extends LoomBuilder<?>> dep : node.dependsOn()) {
                if (!nodes.containsKey(dep)) {
                    throw new UnknownDependencyException(node.name(), dep.getSimpleName());
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    List<DagNode> topologicalSort(Map<Class<? extends LoomBuilder<?>>, DagNode> nodes) {
        Map<Class<? extends LoomBuilder<?>>, Integer> inDegree = new HashMap<>();
        Map<Class<? extends LoomBuilder<?>>, Set<Class<? extends LoomBuilder<?>>>> adjacency = new HashMap<>();

        for (var entry : nodes.entrySet()) {
            inDegree.putIfAbsent(entry.getKey(), 0);
            adjacency.putIfAbsent(entry.getKey(), new HashSet<>());
        }

        for (DagNode node : nodes.values()) {
            for (Class<? extends LoomBuilder<?>> dep : node.dependsOn()) {
                adjacency.computeIfAbsent(dep, k -> new HashSet<>()).add(node.builderClass());
                inDegree.merge(node.builderClass(), 1, Integer::sum);
            }
        }

        Queue<Class<? extends LoomBuilder<?>>> queue = new LinkedList<>();
        for (var entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        List<DagNode> sorted = new ArrayList<>();
        int processedCount = 0;

        while (!queue.isEmpty()) {
            Class<? extends LoomBuilder<?>> current = queue.poll();
            sorted.add(nodes.get(current));
            processedCount++;

            for (Class<? extends LoomBuilder<?>> neighbor : adjacency.getOrDefault(current, Set.of())) {
                int newDegree = inDegree.merge(neighbor, -1, Integer::sum);
                if (newDegree == 0) {
                    queue.add(neighbor);
                }
            }
        }

        if (processedCount != nodes.size()) {
            List<String> cycleNodes = new ArrayList<>();
            for (var entry : inDegree.entrySet()) {
                if (entry.getValue() > 0) {
                    cycleNodes.add(entry.getKey().getSimpleName());
                }
            }
            throw new CycleDetectedException(cycleNodes);
        }

        return sorted;
    }

    DagNode detectTerminalNode(Map<Class<? extends LoomBuilder<?>>, DagNode> nodes,
                               Class<?> responseType) {
        List<DagNode> candidates = nodes.values().stream()
                .filter(n -> n.outputType().equals(responseType))
                .toList();

        if (candidates.isEmpty()) {
            throw new LoomException(
                    "No terminal node found: no builder output type matches response type '"
                    + responseType.getSimpleName() + "'");
        }

        if (candidates.size() > 1) {
            String names = candidates.stream()
                    .map(DagNode::name)
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("");
            throw new LoomException(
                    "Multiple terminal node candidates found with output type '"
                    + responseType.getSimpleName() + "': " + names
                    + ". Exactly one builder must produce the response type.");
        }

        return candidates.get(0);
    }
}
