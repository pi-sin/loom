package io.loom.starter.upstream;

import io.loom.core.exception.LoomException;
import io.loom.core.upstream.UpstreamClient;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class UpstreamClientRegistry {

    private final ConcurrentHashMap<String, UpstreamClient> clients = new ConcurrentHashMap<>();

    public void register(String name, UpstreamClient client) {
        clients.put(name, client);
        log.debug("[Loom] Registered upstream client: {}", name);
    }

    public UpstreamClient getClient(String name) {
        UpstreamClient client = clients.get(name);
        if (client == null) {
            throw new LoomException("Unknown upstream: '" + name
                    + "'. Available upstreams: " + clients.keySet());
        }
        return client;
    }

    public Map<String, UpstreamClient> getAllClients() {
        return Collections.unmodifiableMap(clients);
    }
}
