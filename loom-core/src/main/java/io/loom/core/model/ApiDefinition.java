package io.loom.core.model;

import io.loom.core.engine.Dag;
import java.util.List;

public record ApiDefinition(
    String method,
    String path,
    Class<?> requestType,
    Class<?> responseType,
    String[] middlewares,
    String[] guards,
    Dag dag,
    String summary,
    String description,
    String[] tags,
    List<QueryParamDefinition> queryParams,
    List<HeaderParamDefinition> headerParams
) {
    public RouteDefinition toRoute() {
        return new RouteDefinition(method, path, RouteDefinition.RouteType.BUILDER);
    }
}
