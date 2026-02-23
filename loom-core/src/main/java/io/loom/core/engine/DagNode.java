package io.loom.core.engine;

import io.loom.core.builder.LoomBuilder;
import java.util.Set;

public record DagNode(
    Class<? extends LoomBuilder<?>> builderClass,
    Set<Class<? extends LoomBuilder<?>>> dependsOn,
    boolean required,
    long timeoutMs,
    Class<?> outputType
) {
    public String name() {
        return builderClass.getSimpleName();
    }
}
