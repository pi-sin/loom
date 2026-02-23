package io.loom.starter.web;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class PathMatcherTest {

    @Test
    void shouldMatchExactPath() {
        PathMatcher matcher = new PathMatcher("/api/products");
        assertThat(matcher.matches("/api/products")).isTrue();
        assertThat(matcher.matches("/api/users")).isFalse();
    }

    @Test
    void shouldMatchPathWithVariable() {
        PathMatcher matcher = new PathMatcher("/api/products/{id}");
        assertThat(matcher.matches("/api/products/42")).isTrue();
        assertThat(matcher.matches("/api/products/abc")).isTrue();
        assertThat(matcher.matches("/api/products")).isFalse();
        assertThat(matcher.matches("/api/products/42/extra")).isFalse();
    }

    @Test
    void shouldExtractPathVariables() {
        PathMatcher matcher = new PathMatcher("/api/products/{id}");
        Map<String, String> vars = matcher.extractVariables("/api/products/42");
        assertThat(vars).containsEntry("id", "42");
    }

    @Test
    void shouldExtractMultipleVariables() {
        PathMatcher matcher = new PathMatcher("/api/users/{userId}/orders/{orderId}");
        Map<String, String> vars = matcher.extractVariables("/api/users/123/orders/456");
        assertThat(vars).containsEntry("userId", "123");
        assertThat(vars).containsEntry("orderId", "456");
    }

    @Test
    void shouldReturnEmptyMapWhenNoMatch() {
        PathMatcher matcher = new PathMatcher("/api/products/{id}");
        Map<String, String> vars = matcher.extractVariables("/api/users/42");
        assertThat(vars).isEmpty();
    }

    @Test
    void shouldMatchStaticPath() {
        assertThat(PathMatcher.matches("/api/health", "/api/health")).isTrue();
        assertThat(PathMatcher.matches("/api/health", "/api/other")).isFalse();
    }

    @Test
    void shouldStaticExtractVariables() {
        Map<String, String> vars = PathMatcher.extract("/api/products/{id}", "/api/products/99");
        assertThat(vars).containsEntry("id", "99");
    }
}
