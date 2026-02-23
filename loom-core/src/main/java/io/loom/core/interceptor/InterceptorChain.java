package io.loom.core.interceptor;

public interface InterceptorChain {
    void next(LoomHttpContext context);
}
