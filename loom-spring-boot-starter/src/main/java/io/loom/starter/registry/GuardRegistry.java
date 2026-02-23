package io.loom.starter.registry;

import io.loom.core.annotation.LoomGuard;
import io.loom.core.middleware.Guard;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class GuardRegistry {

    private final Map<String, Guard> guardsByName;

    public GuardRegistry(ApplicationContext applicationContext) {
        this.guardsByName = new HashMap<>(applicationContext.getBeansOfType(Guard.class));
        log.info("[Loom] Registered {} guards", guardsByName.size());
    }

    public Guard getGuard(String name) {
        return guardsByName.get(name);
    }

    public List<Guard> getGuards(String[] names) {
        if (names == null || names.length == 0) {
            return List.of();
        }
        List<Guard> result = new ArrayList<>();
        for (String name : names) {
            Guard guard = guardsByName.get(name);
            if (guard != null) {
                result.add(guard);
            }
        }
        return result;
    }
}
