package io.loom.starter.service;

import io.loom.core.exception.LoomException;
import io.loom.core.exception.LoomRouteNotFoundException;
import io.loom.core.service.RouteConfig;
import io.loom.core.service.ServiceClient;
import io.loom.core.service.ServiceConfig;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class ServiceClientRegistry {

    private final ConcurrentHashMap<String, ServiceClient> clients = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ServiceClient> routeClients = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ServiceConfig> serviceConfigs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, RouteConfig> routeConfigs = new ConcurrentHashMap<>();

    public void register(String name, ServiceClient client) {
        clients.put(name, client);
        log.debug("[Loom] Registered service client: {}", name);
    }

    public void registerRouteClient(String serviceName, String routeName, ServiceClient client) {
        routeClients.put(routeKey(serviceName, routeName), client);
        log.debug("[Loom] Registered route client: {}.{}", serviceName, routeName);
    }

    public void registerServiceConfig(String name, ServiceConfig config) {
        serviceConfigs.put(name, config);
    }

    public void registerRouteConfig(String serviceName, String routeName, RouteConfig config) {
        routeConfigs.put(routeKey(serviceName, routeName), config);
    }

    public ServiceClient getClient(String name) {
        ServiceClient client = clients.get(name);
        if (client == null) {
            throw new LoomException("Unknown service: '" + name
                    + "'. Available services: " + clients.keySet());
        }
        return client;
    }

    /**
     * Returns the client for a specific route. If a route-level client exists
     * (custom timeouts), it is returned; otherwise the service-level client is used.
     */
    public ServiceClient getRouteClient(String serviceName, String routeName) {
        ServiceClient routeClient = routeClients.get(routeKey(serviceName, routeName));
        if (routeClient != null) {
            return routeClient;
        }
        return getClient(serviceName);
    }

    public ServiceConfig getServiceConfig(String name) {
        ServiceConfig config = serviceConfigs.get(name);
        if (config == null) {
            throw new LoomException("Unknown service config: '" + name
                    + "'. Available services: " + serviceConfigs.keySet());
        }
        return config;
    }

    public RouteConfig getRouteConfig(String serviceName, String routeName) {
        RouteConfig config = routeConfigs.get(routeKey(serviceName, routeName));
        if (config != null) {
            return config;
        }
        // Error path: distinguish "unknown service" from "unknown route"
        if (!serviceConfigs.containsKey(serviceName)) {
            throw new LoomException("Unknown service config: '" + serviceName
                    + "'. Available services: " + serviceConfigs.keySet());
        }
        throw new LoomRouteNotFoundException(serviceName, routeName);
    }

    public Map<String, ServiceClient> getAllClients() {
        return Collections.unmodifiableMap(clients);
    }

    private static String routeKey(String serviceName, String routeName) {
        return serviceName + '\0' + routeName;
    }
}
