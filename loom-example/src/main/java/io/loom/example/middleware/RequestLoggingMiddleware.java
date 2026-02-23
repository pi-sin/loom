package io.loom.example.middleware;

import io.loom.core.annotation.LoomMiddleware;
import io.loom.core.middleware.LoomHttpContext;
import io.loom.core.middleware.Middleware;
import io.loom.core.middleware.MiddlewareChain;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@LoomMiddleware(order = 1)
public class RequestLoggingMiddleware implements Middleware {

    @Override
    public void handle(LoomHttpContext context, MiddlewareChain chain) {
        long start = System.currentTimeMillis();
        log.info("[Loom] >> {} {} (requestId={})",
                context.getHttpMethod(), context.getRequestPath(), context.getRequestId());

        chain.next(context);

        long duration = System.currentTimeMillis() - start;
        log.info("[Loom] << {} {} completed in {}ms (status={})",
                context.getHttpMethod(), context.getRequestPath(), duration, context.getResponseStatus());
    }
}
