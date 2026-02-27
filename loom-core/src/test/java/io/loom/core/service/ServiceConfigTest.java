package io.loom.core.service;

import io.loom.core.model.ProxyPathTemplate;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class ServiceConfigTest {

    private final RetryConfig serviceRetry = new RetryConfig(3, 100, 2.0, 5000);
    private final RetryConfig routeRetry = new RetryConfig(5, 200, 3.0, 10000);

    @Test
    void shouldReturnServiceDefaultsWhenRouteInherits() {
        RouteConfig route = new RouteConfig("r1", "/test", "GET",
                RouteConfig.INHERIT, RouteConfig.INHERIT, null,
                ProxyPathTemplate.compile("/test"));

        ServiceConfig service = new ServiceConfig("svc", "http://localhost:8080",
                5000, 30000, serviceRetry, Map.of("r1", route));

        assertThat(service.effectiveConnectTimeout(route)).isEqualTo(5000);
        assertThat(service.effectiveReadTimeout(route)).isEqualTo(30000);
        assertThat(service.effectiveRetry(route)).isSameAs(serviceRetry);
    }

    @Test
    void shouldReturnRouteOverridesWhenSet() {
        RouteConfig route = new RouteConfig("r1", "/test", "GET",
                2000, 3000, routeRetry,
                ProxyPathTemplate.compile("/test"));

        ServiceConfig service = new ServiceConfig("svc", "http://localhost:8080",
                5000, 30000, serviceRetry, Map.of("r1", route));

        assertThat(service.effectiveConnectTimeout(route)).isEqualTo(2000);
        assertThat(service.effectiveReadTimeout(route)).isEqualTo(3000);
        assertThat(service.effectiveRetry(route)).isSameAs(routeRetry);
    }

    @Test
    void shouldDetectRouteNeedingCustomClient() {
        RouteConfig inheritAll = new RouteConfig("r1", "/test", "GET",
                RouteConfig.INHERIT, RouteConfig.INHERIT, null,
                ProxyPathTemplate.compile("/test"));

        RouteConfig customTimeout = new RouteConfig("r2", "/test", "GET",
                2000, RouteConfig.INHERIT, null,
                ProxyPathTemplate.compile("/test"));

        ServiceConfig service = new ServiceConfig("svc", "http://localhost:8080",
                5000, 30000, serviceRetry, Map.of("r1", inheritAll, "r2", customTimeout));

        assertThat(service.routeNeedsCustomClient(inheritAll)).isFalse();
        assertThat(service.routeNeedsCustomClient(customTimeout)).isTrue();
    }

    @Test
    void shouldNotNeedCustomClientWhenRouteTimeoutMatchesServiceDefault() {
        RouteConfig sameTimeout = new RouteConfig("r1", "/test", "GET",
                5000, RouteConfig.INHERIT, null,
                ProxyPathTemplate.compile("/test"));

        ServiceConfig service = new ServiceConfig("svc", "http://localhost:8080",
                5000, 30000, serviceRetry, Map.of("r1", sameTimeout));

        assertThat(service.routeNeedsCustomClient(sameTimeout)).isFalse();
    }

    @Test
    void shouldHandleNullRoute() {
        ServiceConfig service = new ServiceConfig("svc", "http://localhost:8080",
                5000, 30000, serviceRetry, Map.of());

        assertThat(service.effectiveConnectTimeout(null)).isEqualTo(5000);
        assertThat(service.effectiveReadTimeout(null)).isEqualTo(30000);
        assertThat(service.effectiveRetry(null)).isSameAs(serviceRetry);
        assertThat(service.routeNeedsCustomClient(null)).isFalse();
    }

    @Test
    void convenienceConstructorShouldSetDefaults() {
        ServiceConfig service = new ServiceConfig("svc", "http://localhost:8080");

        assertThat(service.connectTimeoutMs()).isEqualTo(5000);
        assertThat(service.readTimeoutMs()).isEqualTo(30000);
        assertThat(service.retry()).isEqualTo(RetryConfig.defaults());
        assertThat(service.routes()).isEmpty();
    }

    @Test
    void routesShouldBeImmutable() {
        RouteConfig route = new RouteConfig("r1", "/test", "GET",
                RouteConfig.INHERIT, RouteConfig.INHERIT, null,
                ProxyPathTemplate.compile("/test"));

        java.util.HashMap<String, RouteConfig> mutableRoutes = new java.util.HashMap<>();
        mutableRoutes.put("r1", route);

        ServiceConfig service = new ServiceConfig("svc", "http://localhost:8080",
                5000, 30000, serviceRetry, mutableRoutes);

        // Mutating the original map should not affect the config
        mutableRoutes.put("r2", route);
        assertThat(service.routes()).hasSize(1);

        // Routes map should be unmodifiable
        assertThatThrownBy(() -> service.routes().put("r2", route))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
