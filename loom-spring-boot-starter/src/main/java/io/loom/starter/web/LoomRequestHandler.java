package io.loom.starter.web;

import io.loom.core.model.ApiDefinition;

import java.util.Map;

import lombok.Getter;

@Getter
public class LoomRequestHandler {

    private final ApiDefinition apiDefinition;
    private final Map<String, String> pathVariables;

    public LoomRequestHandler(ApiDefinition apiDefinition, Map<String, String> pathVariables) {
        this.apiDefinition = apiDefinition;
        this.pathVariables = pathVariables;
    }
}
