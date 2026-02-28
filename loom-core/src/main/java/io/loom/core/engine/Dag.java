package io.loom.core.engine;

import io.loom.core.builder.LoomBuilder;

import java.util.*;

public class Dag {

    private final Map<Class<? extends LoomBuilder<?>>, DagNode> nodes;
    private final List<DagNode> topologicalOrder;
    private final DagNode terminalNode;
    private final int terminalNodeIndex;
    private final Map<Class<?>, Integer> typeIndexMap;
    private final Map<Class<? extends LoomBuilder<?>>, Integer> builderIndexMap;

    public Dag(Map<Class<? extends LoomBuilder<?>>, DagNode> nodes,
               List<DagNode> topologicalOrder,
               DagNode terminalNode,
               Map<Class<?>, Integer> typeIndexMap,
               Map<Class<? extends LoomBuilder<?>>, Integer> builderIndexMap) {
        this.nodes = Collections.unmodifiableMap(new LinkedHashMap<>(nodes));
        this.topologicalOrder = Collections.unmodifiableList(new ArrayList<>(topologicalOrder));
        this.terminalNode = terminalNode;
        this.terminalNodeIndex = terminalNode.index();
        this.typeIndexMap = Map.copyOf(typeIndexMap);
        this.builderIndexMap = Map.copyOf(builderIndexMap);
    }

    public Map<Class<? extends LoomBuilder<?>>, DagNode> getNodes() {
        return nodes;
    }

    public List<DagNode> topologicalOrder() {
        return topologicalOrder;
    }

    public DagNode getTerminalNode() {
        return terminalNode;
    }

    public int terminalNodeIndex() {
        return terminalNodeIndex;
    }

    public DagNode getNode(Class<? extends LoomBuilder<?>> builderClass) {
        return nodes.get(builderClass);
    }

    public int size() {
        return nodes.size();
    }

    public int nodeCount() {
        return nodes.size();
    }

    public Map<Class<?>, Integer> typeIndexMap() {
        return typeIndexMap;
    }

    public Map<Class<? extends LoomBuilder<?>>, Integer> builderIndexMap() {
        return builderIndexMap;
    }
}
