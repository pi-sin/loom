package io.loom.starter.web;

import io.loom.core.model.ApiDefinition;
import io.loom.core.registry.ApiRegistry;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.handler.AbstractHandlerMapping;

import java.util.Map;

@Slf4j
public class LoomHandlerMapping extends AbstractHandlerMapping {

    private final ApiRegistry apiRegistry;
    private volatile RouteTrie routeTrie;

    public LoomHandlerMapping(ApiRegistry apiRegistry) {
        this.apiRegistry = apiRegistry;
        setOrder(Ordered.HIGHEST_PRECEDENCE + 100);
    }

    @Override
    protected Object getHandlerInternal(HttpServletRequest request) {
        RouteTrie trie = getOrBuildTrie();

        String method = request.getMethod();
        String path = request.getRequestURI();

        RouteTrie.RouteMatch match = trie.find(method, path);
        if (match == null) {
            return null;
        }

        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, match.pathVariables());
        ApiDefinition api = match.api();
        log.debug("[Loom] Matched API: {} {} ({})", method, api.path(),
                api.isPassthrough() ? "passthrough" : "builder");
        return new LoomRequestHandler(api, match.pathVariables());
    }

    private RouteTrie getOrBuildTrie() {
        RouteTrie trie = this.routeTrie;
        if (trie != null) {
            return trie;
        }
        synchronized (this) {
            if (this.routeTrie != null) {
                return this.routeTrie;
            }
            trie = new RouteTrie();
            for (ApiDefinition api : apiRegistry.getAllApis()) {
                trie.insert(api);
            }
            this.routeTrie = trie;
            log.info("[Loom] Built route trie with {} routes", apiRegistry.getAllApis().size());
            return trie;
        }
    }
}
