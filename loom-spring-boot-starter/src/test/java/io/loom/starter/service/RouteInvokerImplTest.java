package io.loom.starter.service;

import io.loom.core.model.ProxyPathTemplate;
import io.loom.core.service.RouteConfig;
import io.loom.core.service.ServiceClient;
import io.loom.core.service.ServiceResponse;
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

    // ── ServiceResponse exchange tests ──

    @Test
    void getResponse_shouldReturnServiceResponseOnSuccess() {
        RouteConfig config = routeConfig("/products/{id}");
        ServiceClient client = mock(ServiceClient.class);
        ServiceResponse<String> expected = new ServiceResponse<>(
                "product-data", 200,
                Map.of("X-Request-Id", List.of("req-1")),
                "product-data".getBytes(), "application/json");
        when(client.exchange(eq("GET"), anyString(), any(), eq(String.class), any())).thenReturn(expected);

        RouteInvokerImpl invoker = new RouteInvokerImpl(config, client, Map.of("id", "42"), Map.of());
        ServiceResponse<String> resp = invoker.getResponse(String.class);

        assertThat(resp.isSuccessful()).isTrue();
        assertThat(resp.data()).isEqualTo("product-data");
        assertThat(resp.statusCode()).isEqualTo(200);
        assertThat(resp.headers()).containsKey("X-Request-Id");
        verify(client).exchange("GET", "/products/42", null, String.class, Map.of());
    }

    @Test
    void postResponse_shouldReturnServiceResponseOnError() {
        RouteConfig config = routeConfig("/orders");
        ServiceClient client = mock(ServiceClient.class);
        byte[] errorBody = "{\"error\":\"bad request\"}".getBytes();
        ServiceResponse<String> expected = new ServiceResponse<>(
                null, 400, Map.of(), errorBody, "application/json");
        when(client.exchange(eq("POST"), anyString(), any(), eq(String.class), any())).thenReturn(expected);

        RouteInvokerImpl invoker = new RouteInvokerImpl(config, client, Map.of(), Map.of());
        ServiceResponse<String> resp = invoker.body("payload").postResponse(String.class);

        assertThat(resp.isSuccessful()).isFalse();
        assertThat(resp.isClientError()).isTrue();
        assertThat(resp.data()).isNull();
        assertThat(resp.statusCode()).isEqualTo(400);
        assertThat(resp.rawBody()).isEqualTo(errorBody);
        verify(client).exchange("POST", "/orders", "payload", String.class, Map.of());
    }

    @Test
    void getResponse_shouldPassResolvedPathAndHeaders() {
        RouteConfig config = routeConfig("/products/{id}");
        ServiceClient client = mock(ServiceClient.class);
        ServiceResponse<String> expected = new ServiceResponse<>(
                "ok", 200, Map.of(), "ok".getBytes(), "application/json");
        when(client.exchange(eq("GET"), anyString(), any(), eq(String.class), any())).thenReturn(expected);

        RouteInvokerImpl invoker = new RouteInvokerImpl(config, client,
                Map.of("id", "10"), Map.of("sort", List.of("name")));
        invoker.header("Authorization", "Bearer token").getResponse(String.class);

        verify(client).exchange(eq("GET"),
                argThat(path -> path.contains("/products/10") && path.contains("sort=name")),
                isNull(), eq(String.class),
                eq(Map.of("Authorization", "Bearer token")));
    }

    @Test
    void putResponse_shouldReturnServiceResponse() {
        RouteConfig config = routeConfig("/orders/{id}");
        ServiceClient client = mock(ServiceClient.class);
        ServiceResponse<String> expected = new ServiceResponse<>(
                "updated", 200, Map.of(), "updated".getBytes(), "application/json");
        when(client.exchange(eq("PUT"), anyString(), any(), eq(String.class), any())).thenReturn(expected);

        RouteInvokerImpl invoker = new RouteInvokerImpl(config, client, Map.of("id", "1"), Map.of());
        ServiceResponse<String> resp = invoker.body("payload").putResponse(String.class);

        assertThat(resp.isSuccessful()).isTrue();
        assertThat(resp.data()).isEqualTo("updated");
        verify(client).exchange("PUT", "/orders/1", "payload", String.class, Map.of());
    }

    @Test
    void deleteResponse_shouldReturnServiceResponseOnServerError() {
        RouteConfig config = routeConfig("/orders/{id}");
        ServiceClient client = mock(ServiceClient.class);
        byte[] errorBody = "{\"error\":\"internal\"}".getBytes();
        ServiceResponse<String> expected = new ServiceResponse<>(
                null, 500, Map.of(), errorBody, "application/json");
        when(client.exchange(eq("DELETE"), anyString(), any(), eq(String.class), any())).thenReturn(expected);

        RouteInvokerImpl invoker = new RouteInvokerImpl(config, client, Map.of("id", "5"), Map.of());
        ServiceResponse<String> resp = invoker.deleteResponse(String.class);

        assertThat(resp.isServerError()).isTrue();
        assertThat(resp.data()).isNull();
        assertThat(resp.rawBody()).isEqualTo(errorBody);
        verify(client).exchange("DELETE", "/orders/5", null, String.class, Map.of());
    }

    @Test
    void patchResponse_shouldReturnServiceResponse() {
        RouteConfig config = routeConfig("/orders/{id}");
        ServiceClient client = mock(ServiceClient.class);
        ServiceResponse<String> expected = new ServiceResponse<>(
                "patched", 200, Map.of(), "patched".getBytes(), "application/json");
        when(client.exchange(eq("PATCH"), anyString(), any(), eq(String.class), any())).thenReturn(expected);

        RouteInvokerImpl invoker = new RouteInvokerImpl(config, client, Map.of("id", "3"), Map.of());
        ServiceResponse<String> resp = invoker.body("partial").patchResponse(String.class);

        assertThat(resp.isSuccessful()).isTrue();
        assertThat(resp.data()).isEqualTo("patched");
        verify(client).exchange("PATCH", "/orders/3", "partial", String.class, Map.of());
    }

    @Test
    void getResponse_noBodyPassedToExchange() {
        RouteConfig config = routeConfig("/products");
        ServiceClient client = mock(ServiceClient.class);
        ServiceResponse<String> expected = new ServiceResponse<>(
                "ok", 200, Map.of(), "ok".getBytes(), "application/json");
        when(client.exchange(eq("GET"), anyString(), any(), eq(String.class), any())).thenReturn(expected);

        RouteInvokerImpl invoker = new RouteInvokerImpl(config, client, Map.of(), Map.of());
        invoker.getResponse(String.class);

        // GET should pass null body
        verify(client).exchange("GET", "/products", null, String.class, Map.of());
    }

    @Test
    void deleteResponse_noBodyPassedToExchange() {
        RouteConfig config = routeConfig("/products/{id}");
        ServiceClient client = mock(ServiceClient.class);
        ServiceResponse<String> expected = new ServiceResponse<>(
                "ok", 200, Map.of(), "ok".getBytes(), "application/json");
        when(client.exchange(eq("DELETE"), anyString(), any(), eq(String.class), any())).thenReturn(expected);

        RouteInvokerImpl invoker = new RouteInvokerImpl(config, client, Map.of("id", "1"), Map.of());
        invoker.deleteResponse(String.class);

        // DELETE should pass null body
        verify(client).exchange("DELETE", "/products/1", null, String.class, Map.of());
    }

    private RouteConfig routeConfig(String path) {
        return new RouteConfig("test-route", path, "GET",
                RouteConfig.INHERIT, RouteConfig.INHERIT, null,
                ProxyPathTemplate.compile(path));
    }
}
