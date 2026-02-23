package io.loom.core.model;

public record QueryParamDefinition(
    String name,
    Class<?> type,
    boolean required,
    String defaultValue,
    String description
) {}
