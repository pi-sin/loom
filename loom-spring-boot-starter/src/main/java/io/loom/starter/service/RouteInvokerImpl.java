package io.loom.starter.service;

import io.loom.core.service.RouteConfig;
import io.loom.core.service.RouteInvoker;
import io.loom.core.service.ServiceClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fluent route invoker that auto-forwards incoming path vars and query params,
 * with explicit overrides taking precedence.
 *
 * <p>Explicit override maps are lazily initialized to avoid allocations in the
 * common auto-forward case where no {@code .pathVar()} / {@code .queryParam()} /
 * {@code .header()} calls are made.</p>
 */
public class RouteInvokerImpl implements RouteInvoker {

    private final RouteConfig routeConfig;
    private final ServiceClient client;
    private final Map<String, String> incomingPathVars;
    private final Map<String, List<String>> incomingQueryParams;

    // Lazy-initialized: null until first explicit override call
    private Map<String, String> explicitPathVars;
    private Map<String, String> explicitQueryParams;
    private Map<String, String> explicitHeaders;
    private Object requestBody;

    public RouteInvokerImpl(RouteConfig routeConfig, ServiceClient client,
                             Map<String, String> incomingPathVars,
                             Map<String, List<String>> incomingQueryParams) {
        this.routeConfig = routeConfig;
        this.client = client;
        this.incomingPathVars = incomingPathVars != null ? incomingPathVars : Map.of();
        this.incomingQueryParams = incomingQueryParams != null ? incomingQueryParams : Map.of();
    }

    @Override
    public RouteInvoker pathVar(String name, String value) {
        if (explicitPathVars == null) {
            explicitPathVars = new HashMap<>();
        }
        explicitPathVars.put(name, value);
        return this;
    }

    @Override
    public RouteInvoker queryParam(String name, String value) {
        if (explicitQueryParams == null) {
            explicitQueryParams = new HashMap<>();
        }
        explicitQueryParams.put(name, value);
        return this;
    }

    @Override
    public RouteInvoker header(String name, String value) {
        if (explicitHeaders == null) {
            explicitHeaders = new HashMap<>();
        }
        explicitHeaders.put(name, value);
        return this;
    }

    @Override
    public RouteInvoker body(Object body) {
        this.requestBody = body;
        return this;
    }

    @Override
    public <T> T get(Class<T> responseType) {
        return client.get(resolvedPath(), responseType, headersOrEmpty());
    }

    @Override
    public <T> T post(Class<T> responseType) {
        return client.post(resolvedPath(), requestBody, responseType, headersOrEmpty());
    }

    @Override
    public <T> T put(Class<T> responseType) {
        return client.put(resolvedPath(), requestBody, responseType, headersOrEmpty());
    }

    @Override
    public <T> T delete(Class<T> responseType) {
        return client.delete(resolvedPath(), responseType, headersOrEmpty());
    }

    @Override
    public <T> T patch(Class<T> responseType) {
        return client.patch(resolvedPath(), requestBody, responseType, headersOrEmpty());
    }

    String resolvedPath() {
        Map<String, String> pathVars;
        if (explicitPathVars == null) {
            // No explicit overrides — pass incoming vars directly, zero copy
            pathVars = incomingPathVars;
        } else {
            pathVars = new HashMap<>(incomingPathVars);
            pathVars.putAll(explicitPathVars);
        }

        String queryString = buildQueryString();
        return routeConfig.compiledTemplate().resolve(pathVars, queryString);
    }

    private String buildQueryString() {
        boolean hasExplicit = explicitQueryParams != null;
        boolean hasIncoming = !incomingQueryParams.isEmpty();

        if (!hasExplicit && !hasIncoming) {
            return null;
        }

        // No explicit overrides — build directly from incoming params, no merge map
        if (!hasExplicit) {
            StringBuilder sb = new StringBuilder();
            incomingQueryParams.forEach((key, values) -> {
                if (values != null) {
                    for (String value : values) {
                        if (!sb.isEmpty()) sb.append('&');
                        sb.append(urlEncode(key)).append('=').append(urlEncode(value));
                    }
                }
            });
            return sb.isEmpty() ? null : sb.toString();
        }

        // Merge: incoming as base (all values), explicit overrides replace entire key
        StringBuilder sb = new StringBuilder();
        incomingQueryParams.forEach((key, values) -> {
            if (explicitQueryParams.containsKey(key)) {
                return; // explicit override replaces all incoming values for this key
            }
            if (values != null) {
                for (String value : values) {
                    if (!sb.isEmpty()) sb.append('&');
                    sb.append(urlEncode(key)).append('=').append(urlEncode(value));
                }
            }
        });
        explicitQueryParams.forEach((key, value) -> {
            if (!sb.isEmpty()) sb.append('&');
            sb.append(urlEncode(key)).append('=').append(urlEncode(value));
        });
        return sb.isEmpty() ? null : sb.toString();
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private Map<String, String> headersOrEmpty() {
        if (explicitHeaders == null) {
            return Map.of();
        }
        return Collections.unmodifiableMap(explicitHeaders);
    }
}
