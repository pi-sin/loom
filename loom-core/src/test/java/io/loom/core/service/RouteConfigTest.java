package io.loom.core.service;

import io.loom.core.model.ProxyPathTemplate;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class RouteConfigTest {

    @Test
    void shouldDetectCustomConnectTimeout() {
        RouteConfig route = new RouteConfig("r1", "/test", "GET",
                2000, RouteConfig.INHERIT, null,
                ProxyPathTemplate.compile("/test"));

        assertThat(route.hasCustomConnectTimeout()).isTrue();
        assertThat(route.hasCustomReadTimeout()).isFalse();
        assertThat(route.hasCustomRetry()).isFalse();
    }

    @Test
    void shouldDetectCustomReadTimeout() {
        RouteConfig route = new RouteConfig("r1", "/test", "GET",
                RouteConfig.INHERIT, 3000, null,
                ProxyPathTemplate.compile("/test"));

        assertThat(route.hasCustomConnectTimeout()).isFalse();
        assertThat(route.hasCustomReadTimeout()).isTrue();
    }

    @Test
    void shouldDetectInheritedValues() {
        RouteConfig route = new RouteConfig("r1", "/test", "GET",
                RouteConfig.INHERIT, RouteConfig.INHERIT, null,
                ProxyPathTemplate.compile("/test"));

        assertThat(route.hasCustomConnectTimeout()).isFalse();
        assertThat(route.hasCustomReadTimeout()).isFalse();
        assertThat(route.hasCustomRetry()).isFalse();
    }

    @Test
    void shouldDetectCustomRetry() {
        RetryConfig retry = new RetryConfig(5, 200, 3.0, 10000);
        RouteConfig route = new RouteConfig("r1", "/test", "GET",
                RouteConfig.INHERIT, RouteConfig.INHERIT, retry,
                ProxyPathTemplate.compile("/test"));

        assertThat(route.hasCustomRetry()).isTrue();
    }
}
