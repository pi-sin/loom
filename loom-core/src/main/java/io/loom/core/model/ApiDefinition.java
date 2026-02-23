package io.loom.core.model;

import io.loom.core.engine.Dag;
import io.loom.core.interceptor.LoomInterceptor;
import java.util.List;

public record ApiDefinition(
    String method,
    String path,
    Class<?> requestType,
    Class<?> responseType,
    Class<? extends LoomInterceptor>[] interceptors,
    Dag dag,
    String summary,
    String description,
    String[] tags,
    List<QueryParamDefinition> queryParams,
    List<HeaderParamDefinition> headerParams,
    String upstreamName,
    String upstreamPath
) {
    public boolean isPassthrough() {
        return upstreamName != null && upstreamPath != null;
    }

    public RouteDefinition toRoute() {
        return new RouteDefinition(method, path,
                isPassthrough() ? RouteDefinition.RouteType.PASSTHROUGH : RouteDefinition.RouteType.BUILDER);
    }
}
