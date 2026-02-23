package io.loom.core.exception;

import java.util.List;

public class CycleDetectedException extends LoomException {
    private final List<String> cycle;

    public CycleDetectedException(List<String> cycle) {
        super("Cycle detected in DAG: " + String.join(" -> ", cycle));
        this.cycle = cycle;
    }

    public List<String> getCycle() {
        return cycle;
    }
}
