package io.loom.example.interceptor;

import io.loom.core.interceptor.InterceptorChain;
import io.loom.core.interceptor.LoomHttpContext;
import io.loom.core.interceptor.LoomInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component("apiKeyInterceptor")
public class ApiKeyInterceptor implements LoomInterceptor {

    private static final String VALID_API_KEY = "demo-api-key-12345";

    @Override
    public void handle(LoomHttpContext context, InterceptorChain chain) {
        String apiKey = context.getHeader("X-API-Key");
        if (apiKey == null || !VALID_API_KEY.equals(apiKey)) {
            log.warn("[Loom] Request rejected: invalid or missing API key");
            context.setResponseStatus(403);
            context.setResponseBody(Map.of("error", "Forbidden", "message", "Invalid or missing API key"));
            return; // short-circuit — don't call chain.next()
        }
        chain.next(context);
    }
}
