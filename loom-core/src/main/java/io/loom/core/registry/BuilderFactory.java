package io.loom.core.registry;

import io.loom.core.builder.LoomBuilder;

public interface BuilderFactory {
    <T> LoomBuilder<T> createBuilder(Class<? extends LoomBuilder<T>> builderClass);
    LoomBuilder<?> createBuilderUntyped(Class<? extends LoomBuilder<?>> builderClass);
}
