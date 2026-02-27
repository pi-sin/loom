package io.loom.core.service;

import io.loom.core.model.ProxyPathTemplate;

/**
 * Configuration for a single upstream route within a service.
 * Timeout values of -1 indicate "inherit from service-level defaults".
 */
public record RouteConfig(
    String name,
    String path,
    String method,
    long connectTimeoutMs,
    long readTimeoutMs,
    RetryConfig retry,
    ProxyPathTemplate compiledTemplate
) {
    public static final long INHERIT = -1;

    public boolean hasCustomConnectTimeout() {
        return connectTimeoutMs != INHERIT;
    }

    public boolean hasCustomReadTimeout() {
        return readTimeoutMs != INHERIT;
    }

    public boolean hasCustomRetry() {
        return retry != null;
    }
}
