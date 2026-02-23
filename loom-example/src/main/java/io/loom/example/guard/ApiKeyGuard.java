package io.loom.example.guard;

import io.loom.core.annotation.LoomGuard;
import io.loom.core.middleware.Guard;
import io.loom.core.middleware.LoomHttpContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component("apiKeyGuard")
@LoomGuard(order = 0)
public class ApiKeyGuard implements Guard {

    private static final String VALID_API_KEY = "demo-api-key-12345";

    @Override
    public boolean canActivate(LoomHttpContext context) {
        String apiKey = context.getHeader("X-API-Key");
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("[Loom] Request rejected: missing API key");
            return false;
        }
        if (!VALID_API_KEY.equals(apiKey)) {
            log.warn("[Loom] Request rejected: invalid API key");
            return false;
        }
        return true;
    }
}
