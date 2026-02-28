package io.loom.core.engine;

import io.loom.core.builder.LoomBuilder;
import java.util.Set;

public record DagNode(
    Class<? extends LoomBuilder<?>> builderClass,
    Set<Class<? extends LoomBuilder<?>>> dependsOn,
    boolean required,
    long timeoutMs,
    Class<?> outputType,
    int index,
    int[] dependencyIndices
) {
    public DagNode(Class<? extends LoomBuilder<?>> builderClass,
                   Set<Class<? extends LoomBuilder<?>>> dependsOn,
                   boolean required,
                   long timeoutMs,
                   Class<?> outputType) {
        this(builderClass, dependsOn, required, timeoutMs, outputType, -1, new int[0]);
    }

    public String name() {
        return builderClass.getSimpleName();
    }
}
