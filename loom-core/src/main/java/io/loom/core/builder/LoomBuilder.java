package io.loom.core.builder;

public interface LoomBuilder<O> {
    O build(BuilderContext context);
}
