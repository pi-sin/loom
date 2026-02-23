package io.loom.core.registry;

import io.loom.core.model.ApiDefinition;
import io.loom.core.model.PassthroughDefinition;
import io.loom.core.model.RouteDefinition;
import java.util.List;
import java.util.Optional;

public interface ApiRegistry {
    void registerApi(ApiDefinition api);
    void registerPassthrough(PassthroughDefinition passthrough);

    Optional<ApiDefinition> findApi(String method, String path);
    Optional<PassthroughDefinition> findPassthrough(String method, String path);
    Optional<RouteDefinition> findRoute(String method, String path);

    List<ApiDefinition> getAllApis();
    List<PassthroughDefinition> getAllPassthroughs();
    List<RouteDefinition> getAllRoutes();
}
