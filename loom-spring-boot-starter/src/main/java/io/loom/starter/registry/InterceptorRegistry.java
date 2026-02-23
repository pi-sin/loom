package io.loom.starter.registry;

import io.loom.core.interceptor.LoomInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class InterceptorRegistry {

    private final Map<Class<? extends LoomInterceptor>, LoomInterceptor> interceptorsByClass;
    private final List<LoomInterceptor> globalInterceptors;

    public InterceptorRegistry(ApplicationContext applicationContext) {
        Map<String, LoomInterceptor> beans = applicationContext.getBeansOfType(LoomInterceptor.class);

        this.interceptorsByClass = new HashMap<>();
        for (LoomInterceptor interceptor : beans.values()) {
            interceptorsByClass.put(interceptor.getClass(), interceptor);
        }

        this.globalInterceptors = beans.values().stream()
                .sorted(Comparator.comparingInt(LoomInterceptor::order))
                .collect(Collectors.toList());

        log.info("[Loom] Registered {} interceptors", interceptorsByClass.size());
    }

    public List<LoomInterceptor> getGlobalInterceptors() {
        return Collections.unmodifiableList(globalInterceptors);
    }

    public List<LoomInterceptor> getInterceptors(Class<? extends LoomInterceptor>[] classes) {
        if (classes == null || classes.length == 0) {
            return globalInterceptors;
        }

        Set<Class<? extends LoomInterceptor>> seen = new LinkedHashSet<>();
        List<LoomInterceptor> combined = new ArrayList<>();

        for (LoomInterceptor global : globalInterceptors) {
            combined.add(global);
            seen.add(global.getClass());
        }

        for (Class<? extends LoomInterceptor> clazz : classes) {
            if (!seen.contains(clazz)) {
                LoomInterceptor interceptor = interceptorsByClass.get(clazz);
                if (interceptor != null) {
                    combined.add(interceptor);
                    seen.add(clazz);
                }
            }
        }

        combined.sort(Comparator.comparingInt(LoomInterceptor::order));
        return combined;
    }
}
