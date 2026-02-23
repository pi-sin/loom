package io.loom.example.middleware;

import io.loom.core.annotation.LoomMiddleware;
import io.loom.core.middleware.LoomHttpContext;
import io.loom.core.middleware.Middleware;
import io.loom.core.middleware.MiddlewareChain;
import org.springframework.stereotype.Component;

@Component
@LoomMiddleware(order = 0)
public class CorrelationIdMiddleware implements Middleware {

    @Override
    public void handle(LoomHttpContext context, MiddlewareChain chain) {
        String correlationId = context.getHeader("X-Correlation-ID");
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = context.getRequestId();
        }
        context.setAttribute("correlationId", correlationId);
        context.setResponseHeader("X-Correlation-ID", correlationId);
        chain.next(context);
    }
}
