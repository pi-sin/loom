package io.loom.starter.service;

import io.loom.core.exception.LoomException;
import io.loom.core.exception.LoomRouteNotFoundException;
import io.loom.core.model.ProxyPathTemplate;
import io.loom.core.service.RetryConfig;
import io.loom.core.service.RouteConfig;
import io.loom.core.service.ServiceClient;
import io.loom.core.service.ServiceConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ServiceClientRegistryTest {

    private ServiceClientRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ServiceClientRegistry();
    }

    @Test
    void shouldRegisterAndRetrieveServiceClient() {
        ServiceClient client = mock(ServiceClient.class);
        registry.register("product-service", client);

        assertThat(registry.getClient("product-service")).isSameAs(client);
    }

    @Test
    void shouldThrowForUnknownService() {
        assertThatThrownBy(() -> registry.getClient("unknown"))
                .isInstanceOf(LoomException.class)
                .hasMessageContaining("Unknown service: 'unknown'");
    }

    @Test
    void shouldRegisterAndRetrieveRouteClient() {
        ServiceClient serviceClient = mock(ServiceClient.class);
        ServiceClient routeClient = mock(ServiceClient.class);
        registry.register("svc", serviceClient);
        registry.registerRouteClient("svc", "fast-route", routeClient);

        assertThat(registry.getRouteClient("svc", "fast-route")).isSameAs(routeClient);
    }

    @Test
    void shouldFallBackToServiceClientWhenNoRouteClient() {
        ServiceClient serviceClient = mock(ServiceClient.class);
        registry.register("svc", serviceClient);

        assertThat(registry.getRouteClient("svc", "default-route")).isSameAs(serviceClient);
    }

    @Test
    void shouldRetrieveRouteConfig() {
        RouteConfig route = new RouteConfig("get-product", "/products/{id}", "GET",
                RouteConfig.INHERIT, RouteConfig.INHERIT, null,
                ProxyPathTemplate.compile("/products/{id}"));

        registry.registerRouteConfig("product-service", "get-product", route);

        RouteConfig result = registry.getRouteConfig("product-service", "get-product");
        assertThat(result).isSameAs(route);
    }

    @Test
    void shouldThrowRouteNotFoundForUnknownRoute() {
        // Service exists but route doesn't — should throw RouteNotFoundException
        ServiceConfig config = new ServiceConfig("product-service", "http://localhost:8081");
        registry.registerServiceConfig("product-service", config);

        assertThatThrownBy(() -> registry.getRouteConfig("product-service", "nonexistent"))
                .isInstanceOf(LoomRouteNotFoundException.class)
                .hasMessageContaining("nonexistent")
                .hasMessageContaining("product-service");
    }

    @Test
    void shouldThrowForUnknownServiceWhenLookingUpRoute() {
        // Service doesn't exist at all — should throw LoomException with available services
        ServiceConfig config = new ServiceConfig("other-service", "http://localhost:8081");
        registry.registerServiceConfig("other-service", config);

        assertThatThrownBy(() -> registry.getRouteConfig("unknown-service", "some-route"))
                .isInstanceOf(LoomException.class)
                .hasMessageContaining("Unknown service config: 'unknown-service'")
                .hasMessageContaining("other-service");
    }

    @Test
    void shouldThrowForUnknownServiceConfig() {
        assertThatThrownBy(() -> registry.getServiceConfig("unknown"))
                .isInstanceOf(LoomException.class)
                .hasMessageContaining("Unknown service config: 'unknown'");
    }

    @Test
    void shouldReturnUnmodifiableClientMap() {
        ServiceClient client = mock(ServiceClient.class);
        registry.register("svc", client);

        Map<String, ServiceClient> all = registry.getAllClients();
        assertThat(all).containsKey("svc");
        assertThatThrownBy(() -> all.put("new", client))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
