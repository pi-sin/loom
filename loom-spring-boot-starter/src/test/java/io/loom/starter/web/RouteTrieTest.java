package io.loom.starter.web;

import io.loom.core.model.ApiDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RouteTrieTest {

    private RouteTrie trie;

    @BeforeEach
    void setUp() {
        trie = new RouteTrie();
    }

    private ApiDefinition api(String method, String path) {
        return new ApiDefinition(method, path, null, null, null, null,
                null, null, null, null, null, null, null, null, null);
    }

    @Test
    void matchesStaticRoute() {
        trie.insert(api("GET", "/api/health"));

        RouteTrie.RouteMatch match = trie.find("GET", "/api/health");

        assertThat(match).isNotNull();
        assertThat(match.api().path()).isEqualTo("/api/health");
        assertThat(match.pathVariables()).isEmpty();
    }

    @Test
    void matchesSinglePathVariable() {
        trie.insert(api("GET", "/api/products/{id}"));

        RouteTrie.RouteMatch match = trie.find("GET", "/api/products/42");

        assertThat(match).isNotNull();
        assertThat(match.pathVariables()).containsExactly(Map.entry("id", "42"));
    }

    @Test
    void matchesMultiplePathVariables() {
        trie.insert(api("GET", "/api/users/{userId}/orders/{orderId}"));

        RouteTrie.RouteMatch match = trie.find("GET", "/api/users/123/orders/456");

        assertThat(match).isNotNull();
        assertThat(match.pathVariables())
                .containsExactly(Map.entry("userId", "123"), Map.entry("orderId", "456"));
    }

    @Test
    void literalTakesPriorityOverParam() {
        trie.insert(api("GET", "/api/users/{id}"));
        trie.insert(api("GET", "/api/users/me"));

        RouteTrie.RouteMatch literalMatch = trie.find("GET", "/api/users/me");
        assertThat(literalMatch).isNotNull();
        assertThat(literalMatch.api().path()).isEqualTo("/api/users/me");
        assertThat(literalMatch.pathVariables()).isEmpty();

        RouteTrie.RouteMatch paramMatch = trie.find("GET", "/api/users/42");
        assertThat(paramMatch).isNotNull();
        assertThat(paramMatch.api().path()).isEqualTo("/api/users/{id}");
        assertThat(paramMatch.pathVariables()).containsExactly(Map.entry("id", "42"));
    }

    @Test
    void distinguishesByHttpMethod() {
        trie.insert(api("GET", "/api/orders"));
        trie.insert(api("POST", "/api/orders"));

        assertThat(trie.find("GET", "/api/orders")).isNotNull();
        assertThat(trie.find("GET", "/api/orders").api().method()).isEqualTo("GET");

        assertThat(trie.find("POST", "/api/orders")).isNotNull();
        assertThat(trie.find("POST", "/api/orders").api().method()).isEqualTo("POST");

        assertThat(trie.find("DELETE", "/api/orders")).isNull();
    }

    @Test
    void returnsNullForNoMatch() {
        trie.insert(api("GET", "/api/products/{id}"));

        assertThat(trie.find("GET", "/api/orders/42")).isNull();
        assertThat(trie.find("GET", "/api/products")).isNull();
        assertThat(trie.find("GET", "/api/products/42/reviews")).isNull();
    }

    @Test
    void methodMatchingIsCaseInsensitive() {
        trie.insert(api("GET", "/api/health"));

        assertThat(trie.find("get", "/api/health")).isNotNull();
        assertThat(trie.find("Get", "/api/health")).isNotNull();
    }

    @Test
    void handlesMultipleRoutesWithSharedPrefix() {
        trie.insert(api("GET", "/api/users/{userId}/profile"));
        trie.insert(api("GET", "/api/users/{userId}/orders"));
        trie.insert(api("GET", "/api/users/{userId}/dashboard"));

        RouteTrie.RouteMatch profile = trie.find("GET", "/api/users/1/profile");
        assertThat(profile).isNotNull();
        assertThat(profile.api().path()).isEqualTo("/api/users/{userId}/profile");

        RouteTrie.RouteMatch orders = trie.find("GET", "/api/users/1/orders");
        assertThat(orders).isNotNull();
        assertThat(orders.api().path()).isEqualTo("/api/users/{userId}/orders");

        RouteTrie.RouteMatch dashboard = trie.find("GET", "/api/users/1/dashboard");
        assertThat(dashboard).isNotNull();
        assertThat(dashboard.api().path()).isEqualTo("/api/users/{userId}/dashboard");
    }

    @Test
    void splitPathHandlesEdgeCases() {
        assertThat(RouteTrie.splitPath("/")).isEmpty();
        assertThat(RouteTrie.splitPath("/api")).containsExactly("api");
        assertThat(RouteTrie.splitPath("/api/")).containsExactly("api");
        assertThat(RouteTrie.splitPath("api/health")).containsExactly("api", "health");
    }
}
