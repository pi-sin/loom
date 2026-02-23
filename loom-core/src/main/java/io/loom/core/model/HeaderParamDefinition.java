package io.loom.core.model;

public record HeaderParamDefinition(
    String name,
    boolean required,
    String description
) {}
