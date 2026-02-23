package io.loom.core.model;

public record RouteDefinition(
    String method,
    String path,
    RouteType type
) {
    public enum RouteType {
        BUILDER, PASSTHROUGH
    }
}
