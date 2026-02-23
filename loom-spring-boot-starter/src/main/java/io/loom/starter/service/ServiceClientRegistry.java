package io.loom.starter.service;

import io.loom.core.exception.LoomException;
import io.loom.core.service.ServiceClient;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class ServiceClientRegistry {

    private final ConcurrentHashMap<String, ServiceClient> clients = new ConcurrentHashMap<>();

    public void register(String name, ServiceClient client) {
        clients.put(name, client);
        log.debug("[Loom] Registered service client: {}", name);
    }

    public ServiceClient getClient(String name) {
        ServiceClient client = clients.get(name);
        if (client == null) {
            throw new LoomException("Unknown service: '" + name
                    + "'. Available services: " + clients.keySet());
        }
        return client;
    }

    public Map<String, ServiceClient> getAllClients() {
        return Collections.unmodifiableMap(clients);
    }
}
