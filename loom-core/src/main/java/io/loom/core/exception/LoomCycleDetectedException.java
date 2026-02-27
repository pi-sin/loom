package io.loom.core.exception;

import java.util.List;

import lombok.Getter;

@Getter
public class LoomCycleDetectedException extends LoomException {
    private final List<String> cycle;

    public LoomCycleDetectedException(List<String> cycle) {
        super("Cycle detected in DAG: " + String.join(" -> ", cycle));
        this.cycle = cycle;
    }
}
