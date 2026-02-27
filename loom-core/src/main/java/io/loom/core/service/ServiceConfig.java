package io.loom.core.service;

import java.util.Map;

public record ServiceConfig(
    String name,
    String url,
    long connectTimeoutMs,
    long readTimeoutMs,
    RetryConfig retry,
    Map<String, RouteConfig> routes
) {
    public ServiceConfig {
        routes = routes != null ? Map.copyOf(routes) : Map.of();
    }

    public ServiceConfig(String name, String url) {
        this(name, url, 5000, 30000, RetryConfig.defaults(), Map.of());
    }

    public ServiceConfig(String name, String url, long connectTimeoutMs,
                          long readTimeoutMs, RetryConfig retry) {
        this(name, url, connectTimeoutMs, readTimeoutMs, retry, Map.of());
    }

    /** Returns the effective connect timeout: route override if set, otherwise service default. */
    public long effectiveConnectTimeout(RouteConfig route) {
        if (route != null && route.hasCustomConnectTimeout()) {
            return route.connectTimeoutMs();
        }
        return connectTimeoutMs;
    }

    /** Returns the effective read timeout: route override if set, otherwise service default. */
    public long effectiveReadTimeout(RouteConfig route) {
        if (route != null && route.hasCustomReadTimeout()) {
            return route.readTimeoutMs();
        }
        return readTimeoutMs;
    }

    /** Returns the effective retry config: route override if set, otherwise service default. */
    public RetryConfig effectiveRetry(RouteConfig route) {
        if (route != null && route.hasCustomRetry()) {
            return route.retry();
        }
        return retry;
    }

    /** Whether the route's effective timeouts differ from service defaults. */
    public boolean routeNeedsCustomClient(RouteConfig route) {
        if (route == null) return false;
        return (route.hasCustomConnectTimeout() && route.connectTimeoutMs() != connectTimeoutMs)
            || (route.hasCustomReadTimeout() && route.readTimeoutMs() != readTimeoutMs)
            || (route.hasCustomRetry());
    }
}
