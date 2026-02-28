package io.loom.core.model;

import io.loom.core.exception.LoomException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProxyPathTemplateTest {

    @Test
    void staticPathNoPlaceholders() {
        ProxyPathTemplate t = ProxyPathTemplate.compile("/orders");
        assertThat(t.resolve(Map.of())).isEqualTo("/orders");
    }

    @Test
    void singleVariable() {
        ProxyPathTemplate t = ProxyPathTemplate.compile("/orders/{id}");
        assertThat(t.resolve(Map.of("id", "42"))).isEqualTo("/orders/42");
    }

    @Test
    void multipleVariables() {
        ProxyPathTemplate t = ProxyPathTemplate.compile("/users/{uid}/orders/{oid}");
        assertThat(t.resolve(Map.of("uid", "1", "oid", "42")))
                .isEqualTo("/users/1/orders/42");
    }

    @Test
    void variableAtStart() {
        ProxyPathTemplate t = ProxyPathTemplate.compile("/{version}/orders");
        assertThat(t.resolve(Map.of("version", "v2")))
                .isEqualTo("/v2/orders");
    }

    @Test
    void trailingLiteralAfterVariable() {
        ProxyPathTemplate t = ProxyPathTemplate.compile("/orders/{id}/items");
        assertThat(t.resolve(Map.of("id", "42")))
                .isEqualTo("/orders/42/items");
    }

    @Test
    void missingVariableValueThrowsException() {
        ProxyPathTemplate t = ProxyPathTemplate.compile("/orders/{id}");
        assertThatThrownBy(() -> t.resolve(Map.of()))
                .isInstanceOf(LoomException.class)
                .hasMessageContaining("{id}")
                .hasMessageContaining("/orders/{id}");
    }

    @Test
    void missingOneOfMultipleVariablesThrowsException() {
        ProxyPathTemplate t = ProxyPathTemplate.compile("/users/{uid}/orders/{oid}");
        assertThatThrownBy(() -> t.resolve(Map.of("uid", "1")))
                .isInstanceOf(LoomException.class)
                .hasMessageContaining("{oid}");
    }

    @Test
    void templateRoundtrip() {
        ProxyPathTemplate t = ProxyPathTemplate.compile("/orders/{id}");
        assertThat(t.template()).isEqualTo("/orders/{id}");
    }

    @Test
    void staticPathReturnsSameInstance() {
        ProxyPathTemplate t = ProxyPathTemplate.compile("/orders");
        String first = t.resolve(Map.of());
        String second = t.resolve(Map.of());
        assertThat(first).isSameAs(second);
    }

    @Test
    void unclosedBraceTreatedAsLiteral() {
        ProxyPathTemplate t = ProxyPathTemplate.compile("/orders/{id");
        assertThat(t.resolve(Map.of())).isEqualTo("/orders/{id");
    }

    @Test
    void emptyVariableNameTreatedAsLiteral() {
        ProxyPathTemplate t = ProxyPathTemplate.compile("/orders/{}");
        assertThat(t.resolve(Map.of())).isEqualTo("/orders/{}");
    }

    @Test
    void adjacentVariables() {
        ProxyPathTemplate t = ProxyPathTemplate.compile("/{a}{b}");
        assertThat(t.resolve(Map.of("a", "foo", "b", "bar")))
                .isEqualTo("/foobar");
    }

    @Test
    void resolveWithQueryString() {
        ProxyPathTemplate t = ProxyPathTemplate.compile("/orders/{id}");
        assertThat(t.resolve(Map.of("id", "42"), "status=pending&expand=items"))
                .isEqualTo("/orders/42?status=pending&expand=items");
    }

    @Test
    void resolveWithNullQueryString() {
        ProxyPathTemplate t = ProxyPathTemplate.compile("/orders/{id}");
        assertThat(t.resolve(Map.of("id", "42"), null))
                .isEqualTo("/orders/42");
    }

    @Test
    void staticPathWithQueryString() {
        ProxyPathTemplate t = ProxyPathTemplate.compile("/orders");
        assertThat(t.resolve(Map.of(), "page=1"))
                .isEqualTo("/orders?page=1");
    }

    @Test
    void staticPathWithNullQueryStringReturnsSameInstance() {
        ProxyPathTemplate t = ProxyPathTemplate.compile("/orders");
        String first = t.resolve(Map.of(), null);
        String second = t.resolve(Map.of(), null);
        assertThat(first).isSameAs(second);
    }

    @Test
    void pathVariableWithSpecialCharactersIsEncoded() {
        ProxyPathTemplate t = ProxyPathTemplate.compile("/search/{query}");

        // Spaces encoded as %20
        assertThat(t.resolve(Map.of("query", "hello world")))
                .isEqualTo("/search/hello%20world");

        // Slash encoded as %2F
        assertThat(t.resolve(Map.of("query", "a/b")))
                .isEqualTo("/search/a%2Fb");

        // Question mark encoded as %3F
        assertThat(t.resolve(Map.of("query", "what?")))
                .isEqualTo("/search/what%3F");

        // Hash encoded as %23
        assertThat(t.resolve(Map.of("query", "tag#1")))
                .isEqualTo("/search/tag%231");

        // Unicode encoded
        assertThat(t.resolve(Map.of("query", "caf\u00e9")))
                .isEqualTo("/search/caf%C3%A9");
    }

    @Test
    void typicalPathVariablesPassThroughUnchanged() {
        ProxyPathTemplate t = ProxyPathTemplate.compile("/users/{uid}/orders/{oid}");

        // Numeric IDs
        assertThat(t.resolve(Map.of("uid", "42", "oid", "100")))
                .isEqualTo("/users/42/orders/100");

        // UUIDs
        assertThat(t.resolve(Map.of("uid", "a1b2c3d4-e5f6-7890-abcd-ef1234567890", "oid", "99")))
                .isEqualTo("/users/a1b2c3d4-e5f6-7890-abcd-ef1234567890/orders/99");

        // Alphanumeric with hyphens and underscores
        assertThat(t.resolve(Map.of("uid", "user_name-123", "oid", "order.v2")))
                .isEqualTo("/users/user_name-123/orders/order.v2");
    }
}
