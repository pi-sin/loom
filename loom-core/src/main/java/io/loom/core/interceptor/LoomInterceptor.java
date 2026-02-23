package io.loom.core.interceptor;

public interface LoomInterceptor {
    void handle(LoomHttpContext context, InterceptorChain chain);
    default int order() { return 0; }
}
