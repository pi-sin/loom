package io.loom.core.service;

/**
 * Entry point for route-based service invocation.
 * Obtained via {@code context.service("service-name")}.
 */
public interface ServiceAccessor {
    RouteInvoker route(String routeName);
}
