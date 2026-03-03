package io.loom.example.interceptor;

import io.loom.core.interceptor.InterceptorChain;
import io.loom.core.interceptor.LoomGlobalInterceptor;
import io.loom.core.interceptor.LoomHttpContext;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class CorrelationIdInterceptor implements LoomGlobalInterceptor {

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
