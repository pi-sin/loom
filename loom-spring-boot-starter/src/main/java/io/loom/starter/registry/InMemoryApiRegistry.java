package io.loom.starter.registry;

import io.loom.core.model.ApiDefinition;
import io.loom.core.model.PassthroughDefinition;
import io.loom.core.model.RouteDefinition;
import io.loom.core.registry.ApiRegistry;
import io.loom.starter.web.PathMatcher;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
public class InMemoryApiRegistry implements ApiRegistry {

    private final List<ApiDefinition> apis = new CopyOnWriteArrayList<>();
    private final List<PassthroughDefinition> passthroughs = new CopyOnWriteArrayList<>();

    @Override
    public void registerApi(ApiDefinition api) {
        apis.add(api);
        log.info("[Loom] Registered builder API: {} {}", api.method(), api.path());
    }

    @Override
    public void registerPassthrough(PassthroughDefinition passthrough) {
        passthroughs.add(passthrough);
        log.info("[Loom] Registered passthrough: {} {} -> {}{}",
                passthrough.method(), passthrough.path(),
                passthrough.upstream(), passthrough.upstreamPath());
    }

    @Override
    public Optional<ApiDefinition> findApi(String method, String path) {
        return apis.stream()
                .filter(a -> a.method().equalsIgnoreCase(method) && PathMatcher.matches(a.path(), path))
                .findFirst();
    }

    @Override
    public Optional<PassthroughDefinition> findPassthrough(String method, String path) {
        return passthroughs.stream()
                .filter(p -> p.method().equalsIgnoreCase(method) && PathMatcher.matches(p.path(), path))
                .findFirst();
    }

    @Override
    public Optional<RouteDefinition> findRoute(String method, String path) {
        Optional<ApiDefinition> api = findApi(method, path);
        if (api.isPresent()) {
            return Optional.of(api.get().toRoute());
        }
        return findPassthrough(method, path).map(PassthroughDefinition::toRoute);
    }

    @Override
    public List<ApiDefinition> getAllApis() {
        return Collections.unmodifiableList(apis);
    }

    @Override
    public List<PassthroughDefinition> getAllPassthroughs() {
        return Collections.unmodifiableList(passthroughs);
    }

    @Override
    public List<RouteDefinition> getAllRoutes() {
        List<RouteDefinition> routes = new ArrayList<>();
        apis.forEach(a -> routes.add(a.toRoute()));
        passthroughs.forEach(p -> routes.add(p.toRoute()));
        return Collections.unmodifiableList(routes);
    }
}
