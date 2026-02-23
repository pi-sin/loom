package io.loom.core.middleware;

public interface Interceptor {
    default void preHandle(LoomHttpContext context) {}
    default void postHandle(LoomHttpContext context, Object result) {}
    default void onError(LoomHttpContext context, Throwable error) {}
}
