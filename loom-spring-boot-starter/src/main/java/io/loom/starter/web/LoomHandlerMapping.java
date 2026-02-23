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

    public LoomHandlerMapping(ApiRegistry apiRegistry) {
        this.apiRegistry = apiRegistry;
        setOrder(Ordered.HIGHEST_PRECEDENCE + 100);
    }

    @Override
    protected Object getHandlerInternal(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();

        for (ApiDefinition api : apiRegistry.getAllApis()) {
            PathMatcher matcher = new PathMatcher(api.path());
            if (api.method().equalsIgnoreCase(method) && matcher.matches(path)) {
                Map<String, String> vars = matcher.extractVariables(path);
                request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, vars);
                log.debug("[Loom] Matched API: {} {} ({})", method, api.path(),
                        api.isPassthrough() ? "passthrough" : "builder");
                return new LoomRequestHandler(api, matcher);
            }
        }

        return null;
    }
}
