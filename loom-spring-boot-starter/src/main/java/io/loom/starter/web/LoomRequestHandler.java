package io.loom.starter.web;

import io.loom.core.model.ApiDefinition;
import io.loom.core.model.PassthroughDefinition;

public class LoomRequestHandler {

    private final ApiDefinition apiDefinition;
    private final PassthroughDefinition passthroughDefinition;
    private final PathMatcher pathMatcher;

    public LoomRequestHandler(ApiDefinition apiDefinition, PathMatcher pathMatcher) {
        this.apiDefinition = apiDefinition;
        this.passthroughDefinition = null;
        this.pathMatcher = pathMatcher;
    }

    public LoomRequestHandler(PassthroughDefinition passthroughDefinition, PathMatcher pathMatcher) {
        this.apiDefinition = null;
        this.passthroughDefinition = passthroughDefinition;
        this.pathMatcher = pathMatcher;
    }

    public boolean isPassthrough() {
        return passthroughDefinition != null;
    }

    public ApiDefinition getApiDefinition() {
        return apiDefinition;
    }

    public PassthroughDefinition getPassthroughDefinition() {
        return passthroughDefinition;
    }

    public PathMatcher getPathMatcher() {
        return pathMatcher;
    }
}
