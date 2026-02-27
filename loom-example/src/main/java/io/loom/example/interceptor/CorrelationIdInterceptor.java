package io.loom.example.interceptor;

import io.loom.core.interceptor.InterceptorChain;
import io.loom.core.interceptor.LoomHttpContext;
import io.loom.core.interceptor.LoomInterceptor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class CorrelationIdInterceptor implements LoomInterceptor {

    @Override
    public void handle(LoomHttpContext context, InterceptorChain chain) {
        String correlationId = context.getHeader("X-Correlation-ID");
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        context.setAttribute("correlationId", correlationId);
        context.setResponseHeader("X-Correlation-ID", correlationId);
        chain.next(context);
    }
}
