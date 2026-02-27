package io.loom.example.interceptor;

import io.loom.core.interceptor.InterceptorChain;
import io.loom.core.interceptor.LoomHttpContext;
import io.loom.core.interceptor.LoomInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class RequestLoggingInterceptor implements LoomInterceptor {

    @Override
    public int order() { return 1; }

    @Override
    public void handle(LoomHttpContext context, InterceptorChain chain) {
        long start = System.currentTimeMillis();
        log.info("[Loom] >> {} {}",
                context.getHttpMethod(), context.getRequestPath());

        chain.next(context);

        long duration = System.currentTimeMillis() - start;
        log.info("[Loom] << {} {} completed in {}ms (status={})",
                context.getHttpMethod(), context.getRequestPath(), duration, context.getResponseStatus());
    }
}
