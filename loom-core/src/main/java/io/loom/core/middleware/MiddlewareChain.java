package io.loom.core.middleware;

public interface MiddlewareChain {
    void next(LoomHttpContext context);
}
