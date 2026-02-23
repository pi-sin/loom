package io.loom.starter.registry;

import io.loom.core.annotation.LoomMiddleware;
import io.loom.core.middleware.Middleware;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class MiddlewareRegistry {

    private final Map<String, Middleware> middlewaresByName;
    private final List<Middleware> globalMiddlewares;

    public MiddlewareRegistry(ApplicationContext applicationContext) {
        Map<String, Middleware> beans = applicationContext.getBeansOfType(Middleware.class);
        this.middlewaresByName = new HashMap<>(beans);

        this.globalMiddlewares = beans.values().stream()
                .sorted(Comparator.comparingInt(m -> {
                    LoomMiddleware ann = m.getClass().getAnnotation(LoomMiddleware.class);
                    return ann != null ? ann.order() : 0;
                }))
                .collect(Collectors.toList());

        log.info("[Loom] Registered {} middlewares", middlewaresByName.size());
    }

    public Middleware getMiddleware(String name) {
        return middlewaresByName.get(name);
    }

    public List<Middleware> getGlobalMiddlewares() {
        return Collections.unmodifiableList(globalMiddlewares);
    }

    public List<Middleware> getMiddlewares(String[] names) {
        if (names == null || names.length == 0) {
            return globalMiddlewares;
        }
        List<Middleware> result = new ArrayList<>();
        for (String name : names) {
            Middleware mw = middlewaresByName.get(name);
            if (mw != null) {
                result.add(mw);
            }
        }
        return result;
    }
}
