package io.loom.starter.service;

import io.loom.core.service.RouteConfig;
import io.loom.core.service.RouteInvoker;
import io.loom.core.service.ServiceAccessor;
import io.loom.core.service.ServiceClient;

import java.util.List;
import java.util.Map;

/**
 * Default implementation that looks up route config and client from the registry,
 * then creates a {@link RouteInvokerImpl} with auto-forwarded request context.
 */
public class ServiceAccessorImpl implements ServiceAccessor {

    private final String serviceName;
    private final ServiceClientRegistry registry;
    private final Map<String, String> incomingPathVars;
    private final Map<String, List<String>> incomingQueryParams;

    public ServiceAccessorImpl(String serviceName, ServiceClientRegistry registry,
                                Map<String, String> incomingPathVars,
                                Map<String, List<String>> incomingQueryParams) {
        this.serviceName = serviceName;
        this.registry = registry;
        this.incomingPathVars = incomingPathVars;
        this.incomingQueryParams = incomingQueryParams;
    }

    @Override
    public RouteInvoker route(String routeName) {
        RouteConfig routeConfig = registry.getRouteConfig(serviceName, routeName);
        ServiceClient client = registry.getRouteClient(serviceName, routeName);
        return new RouteInvokerImpl(routeConfig, client, incomingPathVars, incomingQueryParams);
    }
}
