package io.loom.core.model;

public record PassthroughDefinition(
    String method,
    String path,
    String upstream,
    String upstreamPath,
    String summary,
    String description,
    String[] tags
) {
    public RouteDefinition toRoute() {
        return new RouteDefinition(method, path, RouteDefinition.RouteType.PASSTHROUGH);
    }
}
