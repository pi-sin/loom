package io.loom.core.engine;

import io.loom.core.builder.LoomBuilder;

import java.util.*;

public class Dag {

    private final Map<Class<? extends LoomBuilder<?>>, DagNode> nodes;
    private final List<DagNode> topologicalOrder;
    private final DagNode terminalNode;

    public Dag(Map<Class<? extends LoomBuilder<?>>, DagNode> nodes,
               List<DagNode> topologicalOrder,
               DagNode terminalNode) {
        this.nodes = Collections.unmodifiableMap(new LinkedHashMap<>(nodes));
        this.topologicalOrder = Collections.unmodifiableList(new ArrayList<>(topologicalOrder));
        this.terminalNode = terminalNode;
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

    public DagNode getNode(Class<? extends LoomBuilder<?>> builderClass) {
        return nodes.get(builderClass);
    }

    public int size() {
        return nodes.size();
    }
}
