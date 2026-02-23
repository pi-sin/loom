package io.loom.starter.registry;

import io.loom.core.model.ApiDefinition;
import io.loom.core.model.RouteDefinition;
import io.loom.core.registry.ApiRegistry;
import io.loom.starter.web.PathMatcher;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
public class InMemoryApiRegistry implements ApiRegistry {

    private final List<ApiDefinition> apis = new CopyOnWriteArrayList<>();

    @Override
    public void registerApi(ApiDefinition api) {
        apis.add(api);
        if (api.isPassthrough()) {
            log.info("[Loom] Registered passthrough API: {} {} -> {}{}",
                    api.method(), api.path(), api.upstreamName(), api.upstreamPath());
        } else {
            log.info("[Loom] Registered builder API: {} {}", api.method(), api.path());
        }
    }

    @Override
    public Optional<ApiDefinition> findApi(String method, String path) {
        return apis.stream()
                .filter(a -> a.method().equalsIgnoreCase(method) && PathMatcher.matches(a.path(), path))
                .findFirst();
    }

    @Override
    public Optional<RouteDefinition> findRoute(String method, String path) {
        return findApi(method, path).map(ApiDefinition::toRoute);
    }

    @Override
    public List<ApiDefinition> getAllApis() {
        return Collections.unmodifiableList(apis);
    }

    @Override
    public List<RouteDefinition> getAllRoutes() {
        List<RouteDefinition> routes = new ArrayList<>();
        apis.forEach(a -> routes.add(a.toRoute()));
        return Collections.unmodifiableList(routes);
    }
}
