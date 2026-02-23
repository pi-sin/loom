package io.loom.starter.web;

import io.loom.core.model.ApiDefinition;

public class LoomRequestHandler {

    private final ApiDefinition apiDefinition;
    private final PathMatcher pathMatcher;

    public LoomRequestHandler(ApiDefinition apiDefinition, PathMatcher pathMatcher) {
        this.apiDefinition = apiDefinition;
        this.pathMatcher = pathMatcher;
    }

    public ApiDefinition getApiDefinition() {
        return apiDefinition;
    }

    public PathMatcher getPathMatcher() {
        return pathMatcher;
    }
}
