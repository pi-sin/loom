package io.loom.core.middleware;

public interface Middleware {
    void handle(LoomHttpContext context, MiddlewareChain chain);
}
