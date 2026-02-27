package io.loom.starter.service;

import io.loom.core.model.ProxyPathTemplate;
import io.loom.core.service.RouteConfig;
import io.loom.core.service.ServiceClient;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RouteInvokerImplTest {

    @Test
    void shouldAutoForwardPathVarsFromIncomingRequest() {
        RouteConfig config = routeConfig("/products/{id}");
        ServiceClient client = mock(ServiceClient.class);
        when(client.get(anyString(), eq(String.class), any())).thenReturn("ok");

        Map<String, String> incomingPathVars = Map.of("id", "42");

        RouteInvokerImpl invoker = new RouteInvokerImpl(config, client, incomingPathVars, Map.of());
        invoker.get(String.class);

        verify(client).get("/products/42", String.class, Map.of());
    }

    @Test
    void shouldAllowExplicitPathVarOverride() {
        RouteConfig config = routeConfig("/products/{id}");
        ServiceClient client = mock(ServiceClient.class);
        when(client.get(anyString(), eq(String.class), any())).thenReturn("ok");

        Map<String, String> incomingPathVars = Map.of("id", "42");

        RouteInvokerImpl invoker = new RouteInvokerImpl(config, client, incomingPathVars, Map.of());
        invoker.pathVar("id", "99").get(String.class);

        verify(client).get("/products/99", String.class, Map.of());
    }

    @Test
    void shouldAutoForwardQueryParams() {
        RouteConfig config = routeConfig("/products");
        ServiceClient client = mock(ServiceClient.class);
        when(client.get(anyString(), eq(String.class), any())).thenReturn("ok");

        Map<String, List<String>> incomingQueryParams = Map.of("category", List.of("electronics"));

        RouteInvokerImpl invoker = new RouteInvokerImpl(config, client, Map.of(), incomingQueryParams);
        invoker.get(String.class);

        verify(client).get("/products?category=electronics", String.class, Map.of());
    }

    @Test
    void shouldAllowExplicitQueryParamOverride() {
        RouteConfig config = routeConfig("/products");
        ServiceClient client = mock(ServiceClient.class);
        when(client.get(anyString(), eq(String.class), any())).thenReturn("ok");

        Map<String, List<String>> incomingQueryParams = Map.of("category", List.of("electronics"));

        RouteInvokerImpl invoker = new RouteInvokerImpl(config, client, Map.of(), incomingQueryParams);
        invoker.queryParam("category", "books").get(String.class);

        verify(client).get("/products?category=books", String.class, Map.of());
    }

    @Test
    void shouldMergeExplicitAndAutoForwardedQueryParams() {
        RouteConfig config = routeConfig("/products");
        ServiceClient client = mock(ServiceClient.class);
        when(client.get(anyString(), eq(String.class), any())).thenReturn("ok");

        Map<String, List<String>> incomingQueryParams = Map.of("category", List.of("electronics"));

        RouteInvokerImpl invoker = new RouteInvokerImpl(config, client, Map.of(), incomingQueryParams);
        invoker.queryParam("sort", "price").get(String.class);

        verify(client).get(argThat(path -> {
            // Both params should be present
            return path.startsWith("/products?") &&
                   path.contains("category=electronics") &&
                   path.contains("sort=price");
        }), eq(String.class), eq(Map.of()));
    }

    @Test
    void shouldForwardHeadersOnlyWhenExplicit() {
        RouteConfig config = routeConfig("/products");
        ServiceClient client = mock(ServiceClient.class);
        when(client.get(anyString(), eq(String.class), any())).thenReturn("ok");

        // No explicit headers → empty map
        RouteInvokerImpl invoker = new RouteInvokerImpl(config, client, Map.of(), Map.of());
        invoker.get(String.class);
        verify(client).get("/products", String.class, Map.of());

        // With explicit header
        reset(client);
        when(client.get(anyString(), eq(String.class), any())).thenReturn("ok");
        RouteInvokerImpl invoker2 = new RouteInvokerImpl(config, client, Map.of(), Map.of());
        invoker2.header("X-Custom", "val").get(String.class);
        verify(client).get("/products", String.class, Map.of("X-Custom", "val"));
    }

    @Test
    void shouldInvokePostWithBody() {
        RouteConfig config = routeConfig("/orders");
        ServiceClient client = mock(ServiceClient.class);
        when(client.post(anyString(), any(), eq(String.class), any())).thenReturn("created");

        RouteInvokerImpl invoker = new RouteInvokerImpl(config, client, Map.of(), Map.of());
        String result = invoker.body("payload").post(String.class);

        assertThat(result).isEqualTo("created");
        verify(client).post("/orders", "payload", String.class, Map.of());
    }

    @Test
    void shouldInvokePutWithBody() {
        RouteConfig config = routeConfig("/orders/{id}");
        ServiceClient client = mock(ServiceClient.class);
        when(client.put(anyString(), any(), eq(String.class), any())).thenReturn("updated");

        RouteInvokerImpl invoker = new RouteInvokerImpl(config, client, Map.of("id", "1"), Map.of());
        String result = invoker.body("payload").put(String.class);

        assertThat(result).isEqualTo("updated");
        verify(client).put("/orders/1", "payload", String.class, Map.of());
    }

    @Test
    void shouldInvokeDelete() {
        RouteConfig config = routeConfig("/orders/{id}");
        ServiceClient client = mock(ServiceClient.class);
        when(client.delete(anyString(), eq(String.class), any())).thenReturn("deleted");

        RouteInvokerImpl invoker = new RouteInvokerImpl(config, client, Map.of("id", "5"), Map.of());
        String result = invoker.delete(String.class);

        assertThat(result).isEqualTo("deleted");
        verify(client).delete("/orders/5", String.class, Map.of());
    }

    @Test
    void shouldInvokePatchWithBody() {
        RouteConfig config = routeConfig("/orders/{id}");
        ServiceClient client = mock(ServiceClient.class);
        when(client.patch(anyString(), any(), eq(String.class), any())).thenReturn("patched");

        RouteInvokerImpl invoker = new RouteInvokerImpl(config, client, Map.of("id", "3"), Map.of());
        String result = invoker.body("partial").patch(String.class);

        assertThat(result).isEqualTo("patched");
        verify(client).patch("/orders/3", "partial", String.class, Map.of());
    }

    @Test
    void shouldUrlEncodeQueryParamsWithSpaces() {
        RouteConfig config = routeConfig("/products");
        ServiceClient client = mock(ServiceClient.class);
        when(client.get(anyString(), eq(String.class), any())).thenReturn("ok");

        Map<String, List<String>> incomingQueryParams = Map.of("filter", List.of("foo bar"));

        RouteInvokerImpl invoker = new RouteInvokerImpl(config, client, Map.of(), incomingQueryParams);
        invoker.get(String.class);

        verify(client).get("/products?filter=foo+bar", String.class, Map.of());
    }

    @Test
    void shouldUrlEncodeQueryParamsWithAmpersand() {
        RouteConfig config = routeConfig("/products");
        ServiceClient client = mock(ServiceClient.class);
        when(client.get(anyString(), eq(String.class), any())).thenReturn("ok");

        Map<String, List<String>> incomingQueryParams = Map.of("filter", List.of("a&b"));

        RouteInvokerImpl invoker = new RouteInvokerImpl(config, client, Map.of(), incomingQueryParams);
        invoker.get(String.class);

        verify(client).get("/products?filter=a%26b", String.class, Map.of());
    }

    @Test
    void shouldUrlEncodeQueryParamsWithEquals() {
        RouteConfig config = routeConfig("/products");
        ServiceClient client = mock(ServiceClient.class);
        when(client.get(anyString(), eq(String.class), any())).thenReturn("ok");

        Map<String, List<String>> incomingQueryParams = Map.of("expr", List.of("x=1"));

        RouteInvokerImpl invoker = new RouteInvokerImpl(config, client, Map.of(), incomingQueryParams);
        invoker.get(String.class);

        verify(client).get("/products?expr=x%3D1", String.class, Map.of());
    }

    @Test
    void shouldUrlEncodeQueryParamsWithUnicode() {
        RouteConfig config = routeConfig("/products");
        ServiceClient client = mock(ServiceClient.class);
        when(client.get(anyString(), eq(String.class), any())).thenReturn("ok");

        Map<String, List<String>> incomingQueryParams = Map.of("name", List.of("\u00e9clair"));

        RouteInvokerImpl invoker = new RouteInvokerImpl(config, client, Map.of(), incomingQueryParams);
        invoker.get(String.class);

        verify(client).get("/products?name=%C3%A9clair", String.class, Map.of());
    }

    @Test
    void shouldUrlEncodeMergedQueryParams() {
        RouteConfig config = routeConfig("/products");
        ServiceClient client = mock(ServiceClient.class);
        when(client.get(anyString(), eq(String.class), any())).thenReturn("ok");

        Map<String, List<String>> incomingQueryParams = Map.of("filter", List.of("foo bar"));

        RouteInvokerImpl invoker = new RouteInvokerImpl(config, client, Map.of(), incomingQueryParams);
        invoker.queryParam("tag", "a&b").get(String.class);

        verify(client).get(argThat(path ->
                path.startsWith("/products?") &&
                path.contains("filter=foo+bar") &&
                path.contains("tag=a%26b")
        ), eq(String.class), eq(Map.of()));
    }

    @Test
    void shouldForwardAllValuesOfMultiValuedQueryParam() {
        RouteConfig config = routeConfig("/products");
        ServiceClient client = mock(ServiceClient.class);
        when(client.get(anyString(), eq(String.class), any())).thenReturn("ok");

        Map<String, List<String>> incomingQueryParams = Map.of("tag", List.of("a", "b", "c"));

        RouteInvokerImpl invoker = new RouteInvokerImpl(config, client, Map.of(), incomingQueryParams);
        invoker.get(String.class);

        verify(client).get(argThat(path ->
                path.startsWith("/products?") &&
                path.contains("tag=a") &&
                path.contains("tag=b") &&
                path.contains("tag=c")
        ), eq(String.class), eq(Map.of()));
    }

    @Test
    void explicitOverrideShouldReplaceAllMultiValuesForKey() {
        RouteConfig config = routeConfig("/products");
        ServiceClient client = mock(ServiceClient.class);
        when(client.get(anyString(), eq(String.class), any())).thenReturn("ok");

        Map<String, List<String>> incomingQueryParams = Map.of("tag", List.of("a", "b"));

        RouteInvokerImpl invoker = new RouteInvokerImpl(config, client, Map.of(), incomingQueryParams);
        invoker.queryParam("tag", "override").get(String.class);

        verify(client).get("/products?tag=override", String.class, Map.of());
    }

    @Test
    void shouldHandleNullIncomingContext() {
        RouteConfig config = routeConfig("/products/{id}");
        ServiceClient client = mock(ServiceClient.class);
        when(client.get(anyString(), eq(String.class), any())).thenReturn("ok");

        RouteInvokerImpl invoker = new RouteInvokerImpl(config, client, null, null);
        invoker.pathVar("id", "7").get(String.class);

        verify(client).get("/products/7", String.class, Map.of());
    }

    private RouteConfig routeConfig(String path) {
        return new RouteConfig("test-route", path, "GET",
                RouteConfig.INHERIT, RouteConfig.INHERIT, null,
                ProxyPathTemplate.compile(path));
    }
}
