package io.loom.starter.web;

import io.loom.core.codec.JsonCodec;
import io.loom.core.engine.Dag;
import io.loom.core.engine.DagExecutor;
import io.loom.core.exception.LoomException;
import io.loom.core.exception.LoomServiceClientException;
import io.loom.core.interceptor.InterceptorChain;
import io.loom.core.interceptor.LoomHttpContext;
import io.loom.core.interceptor.LoomInterceptor;
import io.loom.core.model.ApiDefinition;
import io.loom.core.model.ProxyPathTemplate;
import io.loom.core.service.ServiceClient;
import io.loom.core.service.ServiceResponse;
import io.loom.starter.registry.InterceptorRegistry;
import io.loom.starter.service.ServiceClientRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class LoomHandlerAdapterTest {

    private DagExecutor dagExecutor;
    private InterceptorRegistry interceptorRegistry;
    private ServiceClientRegistry serviceClientRegistry;
    private JsonCodec jsonCodec;
    private LoomHandlerAdapter adapter;

    private static final long MAX_BODY_SIZE = 10_485_760L;

    @BeforeEach
    void setUp() {
        dagExecutor = mock(DagExecutor.class);
        interceptorRegistry = mock(InterceptorRegistry.class);
        serviceClientRegistry = mock(ServiceClientRegistry.class);
        jsonCodec = mock(JsonCodec.class);

        // Return empty interceptor list so the chain immediately runs the terminal action
        when(interceptorRegistry.getInterceptors(any())).thenReturn(List.of());

        adapter = new LoomHandlerAdapter(dagExecutor, interceptorRegistry,
                serviceClientRegistry, jsonCodec, MAX_BODY_SIZE);
    }

    private MockHttpServletRequest createRequest(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setContent(new byte[0]);
        return request;
    }

    private LoomRequestHandler builderHandler(String method, String path) {
        Dag dag = mock(Dag.class);
        ApiDefinition api = new ApiDefinition(method, path, null, null, null, dag,
                null, null, null, null, null, null, null, null, null);
        return new LoomRequestHandler(api, Map.of());
    }

    private LoomRequestHandler passthroughHandler(String method, String path,
                                                   String serviceName, String routeName) {
        ProxyPathTemplate template = ProxyPathTemplate.compile("/upstream/path");
        ApiDefinition api = new ApiDefinition(method, path, null, null, null, null,
                null, null, null, null, null, serviceName, routeName, template, null);
        return new LoomRequestHandler(api, Map.of());
    }

    // ── Builder path exception tests ──

    @Test
    void builderPath_loomExceptionGetsApiRouteEnriched() {
        LoomException original = new LoomException("DAG node failed");
        when(dagExecutor.execute(any(), any())).thenThrow(original);

        MockHttpServletRequest request = createRequest("GET", "/api/test");
        MockHttpServletResponse response = new MockHttpServletResponse();
        LoomRequestHandler handler = builderHandler("GET", "/api/test");

        assertThatThrownBy(() -> adapter.handle(request, response, handler))
                .isInstanceOf(LoomException.class)
                .satisfies(ex -> {
                    LoomException loomEx = (LoomException) ex;
                    assertThat(loomEx.getApiRoute()).isEqualTo("GET /api/test");
                });
    }

    @Test
    void builderPath_nonLoomExceptionWrappedWithApiRoute() {
        when(dagExecutor.execute(any(), any())).thenThrow(new NullPointerException("oops"));

        MockHttpServletRequest request = createRequest("GET", "/api/test");
        MockHttpServletResponse response = new MockHttpServletResponse();
        LoomRequestHandler handler = builderHandler("GET", "/api/test");

        assertThatThrownBy(() -> adapter.handle(request, response, handler))
                .isInstanceOf(LoomException.class)
                .hasMessageContaining("Builder execution failed")
                .hasCauseInstanceOf(NullPointerException.class)
                .satisfies(ex -> {
                    LoomException loomEx = (LoomException) ex;
                    assertThat(loomEx.getApiRoute()).isEqualTo("GET /api/test");
                });
    }

    // ── Passthrough path exception tests ──

    @Test
    void passthroughPath_loomExceptionGetsApiRouteEnriched() {
        ServiceClient client = mock(ServiceClient.class);
        when(serviceClientRegistry.getRouteClient("test-svc", "get-all")).thenReturn(client);
        LoomServiceClientException original = new LoomServiceClientException("test-svc", 502, "Bad Gateway");
        when(client.proxy(any(), any(), any(), any())).thenThrow(original);

        MockHttpServletRequest request = createRequest("GET", "/api/proxy");
        MockHttpServletResponse response = new MockHttpServletResponse();
        LoomRequestHandler handler = passthroughHandler("GET", "/api/proxy", "test-svc", "get-all");

        assertThatThrownBy(() -> adapter.handle(request, response, handler))
                .isInstanceOf(LoomServiceClientException.class)
                .satisfies(ex -> {
                    LoomException loomEx = (LoomException) ex;
                    assertThat(loomEx.getApiRoute()).isEqualTo("GET /api/proxy");
                });
    }

    @Test
    void passthroughPath_nonLoomExceptionWrappedWithApiRoute() {
        ServiceClient client = mock(ServiceClient.class);
        when(serviceClientRegistry.getRouteClient("test-svc", "get-all")).thenReturn(client);
        when(client.proxy(any(), any(), any(), any())).thenThrow(new RuntimeException("connection reset"));

        MockHttpServletRequest request = createRequest("GET", "/api/proxy");
        MockHttpServletResponse response = new MockHttpServletResponse();
        LoomRequestHandler handler = passthroughHandler("GET", "/api/proxy", "test-svc", "get-all");

        assertThatThrownBy(() -> adapter.handle(request, response, handler))
                .isInstanceOf(LoomException.class)
                .hasMessageContaining("Proxy call failed")
                .hasCauseInstanceOf(RuntimeException.class)
                .satisfies(ex -> {
                    LoomException loomEx = (LoomException) ex;
                    assertThat(loomEx.getApiRoute()).isEqualTo("GET /api/proxy");
                });
    }

    // ── Passthrough header forwarding tests ──

    @Test
    void passthroughPath_singleValueHeaderNotJoined() throws Exception {
        ServiceClient client = mock(ServiceClient.class);
        when(serviceClientRegistry.getRouteClient("test-svc", "get-all")).thenReturn(client);
        ServiceResponse<byte[]> upstream = new ServiceResponse<>(
                "{\"status\":\"ok\"}".getBytes(), 200, Map.of(), "{\"status\":\"ok\"}".getBytes(), "application/json");
        when(client.proxy(any(), any(), any(), any())).thenReturn(upstream);

        MockHttpServletRequest request = createRequest("GET", "/api/proxy");
        request.addHeader("X-Custom", "single-value");
        MockHttpServletResponse response = new MockHttpServletResponse();
        LoomRequestHandler handler = passthroughHandler("GET", "/api/proxy", "test-svc", "get-all");

        adapter.handle(request, response, handler);

        @SuppressWarnings("unchecked")
        var headersCaptor = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(client).proxy(any(), any(), any(), headersCaptor.capture());

        @SuppressWarnings("unchecked")
        Map<String, String> forwardedHeaders = headersCaptor.getValue();
        assertThat(forwardedHeaders.get("X-Custom")).isEqualTo("single-value");
    }

    @Test
    void passthroughPath_hopByHopHeadersFiltered() throws Exception {
        ServiceClient client = mock(ServiceClient.class);
        when(serviceClientRegistry.getRouteClient("test-svc", "get-all")).thenReturn(client);
        ServiceResponse<byte[]> upstream = new ServiceResponse<>(
                "{\"status\":\"ok\"}".getBytes(), 200, Map.of(), "{\"status\":\"ok\"}".getBytes(), "application/json");
        when(client.proxy(any(), any(), any(), any())).thenReturn(upstream);

        MockHttpServletRequest request = createRequest("GET", "/api/proxy");
        request.addHeader("Host", "localhost:8080");
        request.addHeader("Content-Length", "0");
        request.addHeader("Connection", "keep-alive");
        request.addHeader("X-Forwarded-For", "192.168.1.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        LoomRequestHandler handler = passthroughHandler("GET", "/api/proxy", "test-svc", "get-all");

        adapter.handle(request, response, handler);

        @SuppressWarnings("unchecked")
        var headersCaptor = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(client).proxy(any(), any(), any(), headersCaptor.capture());

        @SuppressWarnings("unchecked")
        Map<String, String> forwardedHeaders = headersCaptor.getValue();
        assertThat(forwardedHeaders).doesNotContainKey("Host")
                .doesNotContainKey("host")
                .doesNotContainKey("Content-Length")
                .doesNotContainKey("content-length")
                .doesNotContainKey("Connection")
                .doesNotContainKey("connection");
        assertThat(forwardedHeaders).containsKey("X-Forwarded-For");
    }

    // ── Interceptor short-circuit test ──

    @Test
    void interceptorShortCircuitPreservesResponseBody() throws Exception {
        // Interceptor sets response body and does NOT call chain.next()
        LoomInterceptor shortCircuitInterceptor = new LoomInterceptor() {
            @Override
            public void handle(LoomHttpContext ctx, InterceptorChain chain) {
                ctx.setResponseBody(Map.of("error", "unauthorized"));
                ctx.setResponseStatus(401);
                // Deliberately NOT calling chain.next() — short-circuit
            }
        };
        when(interceptorRegistry.getInterceptors(any())).thenReturn(List.of(shortCircuitInterceptor));

        MockHttpServletRequest request = createRequest("GET", "/api/test");
        MockHttpServletResponse response = new MockHttpServletResponse();
        LoomRequestHandler handler = builderHandler("GET", "/api/test");

        adapter.handle(request, response, handler);

        assertThat(response.getStatus()).isEqualTo(401);
        // Verify DAG was never executed
        verify(dagExecutor, never()).execute(any(), any());
    }

    @Test
    void maxRequestBodySizePassedToContext() {
        // Verify that a body exceeding maxRequestBodySize is rejected
        // This proves the adapter wires maxRequestBodySize through to the context
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/test");
        request.setContent(new byte[100]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        LoomRequestHandler handler = builderHandler("POST", "/api/test");

        // Use a very small max size to trigger rejection
        LoomHandlerAdapter smallAdapter = new LoomHandlerAdapter(dagExecutor, interceptorRegistry,
                serviceClientRegistry, jsonCodec, 50);

        assertThatThrownBy(() -> smallAdapter.handle(request, response, handler))
                .isInstanceOf(LoomException.class)
                .hasMessageContaining("Request body too large");
    }

    // ── New passthrough response forwarding tests ──

    @Test
    void passthroughPath_forwardsUpstreamStatusCode() throws Exception {
        ServiceClient client = mock(ServiceClient.class);
        when(serviceClientRegistry.getRouteClient("test-svc", "get-all")).thenReturn(client);
        ServiceResponse<byte[]> upstream = new ServiceResponse<>(
                "Not Found".getBytes(), 404, Map.of(), "Not Found".getBytes(), "application/json");
        when(client.proxy(any(), any(), any(), any())).thenReturn(upstream);

        MockHttpServletRequest request = createRequest("GET", "/api/proxy");
        MockHttpServletResponse response = new MockHttpServletResponse();
        LoomRequestHandler handler = passthroughHandler("GET", "/api/proxy", "test-svc", "get-all");

        adapter.handle(request, response, handler);

        assertThat(response.getStatus()).isEqualTo(404);
    }

    @Test
    void passthroughPath_forwardsUpstreamResponseHeaders() throws Exception {
        ServiceClient client = mock(ServiceClient.class);
        when(serviceClientRegistry.getRouteClient("test-svc", "get-all")).thenReturn(client);
        Map<String, List<String>> upstreamHeaders = Map.of(
                "Cache-Control", List.of("max-age=3600"),
                "X-Request-Id", List.of("abc-123")
        );
        ServiceResponse<byte[]> upstream = new ServiceResponse<>(
                "{}".getBytes(), 200, upstreamHeaders, "{}".getBytes(), "application/json");
        when(client.proxy(any(), any(), any(), any())).thenReturn(upstream);

        MockHttpServletRequest request = createRequest("GET", "/api/proxy");
        MockHttpServletResponse response = new MockHttpServletResponse();
        LoomRequestHandler handler = passthroughHandler("GET", "/api/proxy", "test-svc", "get-all");

        adapter.handle(request, response, handler);

        assertThat(response.getHeader("Cache-Control")).isEqualTo("max-age=3600");
        assertThat(response.getHeader("X-Request-Id")).isEqualTo("abc-123");
    }

    @Test
    void passthroughPath_filtersHopByHopResponseHeaders() throws Exception {
        ServiceClient client = mock(ServiceClient.class);
        when(serviceClientRegistry.getRouteClient("test-svc", "get-all")).thenReturn(client);
        Map<String, List<String>> upstreamHeaders = Map.of(
                "Transfer-Encoding", List.of("chunked"),
                "Connection", List.of("keep-alive"),
                "X-Custom", List.of("value")
        );
        ServiceResponse<byte[]> upstream = new ServiceResponse<>(
                "{}".getBytes(), 200, upstreamHeaders, "{}".getBytes(), "application/json");
        when(client.proxy(any(), any(), any(), any())).thenReturn(upstream);

        MockHttpServletRequest request = createRequest("GET", "/api/proxy");
        MockHttpServletResponse response = new MockHttpServletResponse();
        LoomRequestHandler handler = passthroughHandler("GET", "/api/proxy", "test-svc", "get-all");

        adapter.handle(request, response, handler);

        assertThat(response.getHeader("Transfer-Encoding")).isNull();
        assertThat(response.getHeader("Connection")).isNull();
        assertThat(response.getHeader("X-Custom")).isEqualTo("value");
    }

    @Test
    void passthroughPath_preservesNonJsonContentType() throws Exception {
        ServiceClient client = mock(ServiceClient.class);
        when(serviceClientRegistry.getRouteClient("test-svc", "get-all")).thenReturn(client);
        byte[] xmlBody = "<root><status>ok</status></root>".getBytes();
        ServiceResponse<byte[]> upstream = new ServiceResponse<>(
                xmlBody, 200, Map.of(), xmlBody, "application/xml");
        when(client.proxy(any(), any(), any(), any())).thenReturn(upstream);

        MockHttpServletRequest request = createRequest("GET", "/api/proxy");
        MockHttpServletResponse response = new MockHttpServletResponse();
        LoomRequestHandler handler = passthroughHandler("GET", "/api/proxy", "test-svc", "get-all");

        adapter.handle(request, response, handler);

        assertThat(response.getContentType()).isEqualTo("application/xml");
        assertThat(response.getContentAsString()).isEqualTo("<root><status>ok</status></root>");
    }

    @Test
    void passthroughPath_contentTypeNotDuplicatedInResponseHeaders() throws Exception {
        ServiceClient client = mock(ServiceClient.class);
        when(serviceClientRegistry.getRouteClient("test-svc", "get-all")).thenReturn(client);
        // Upstream includes Content-Type in both the headers map and contentType field
        Map<String, List<String>> upstreamHeaders = Map.of(
                "Content-Type", List.of("application/xml"),
                "X-Custom", List.of("value")
        );
        ServiceResponse<byte[]> upstream = new ServiceResponse<>(
                "<ok/>".getBytes(), 200, upstreamHeaders, "<ok/>".getBytes(), "application/xml");
        when(client.proxy(any(), any(), any(), any())).thenReturn(upstream);

        MockHttpServletRequest request = createRequest("GET", "/api/proxy");
        MockHttpServletResponse response = new MockHttpServletResponse();
        LoomRequestHandler handler = passthroughHandler("GET", "/api/proxy", "test-svc", "get-all");

        adapter.handle(request, response, handler);

        // Content-Type should appear exactly once (set via setContentType, not addHeader)
        assertThat(response.getContentType()).isEqualTo("application/xml");
        assertThat(response.getHeaders("Content-Type")).hasSize(1);
        // Other headers still forwarded
        assertThat(response.getHeader("X-Custom")).isEqualTo("value");
    }

    @Test
    void passthroughPath_contentTypeFilteringIsCaseInsensitive() throws Exception {
        ServiceClient client = mock(ServiceClient.class);
        when(serviceClientRegistry.getRouteClient("test-svc", "get-all")).thenReturn(client);
        // Mixed-case content-type key in upstream headers
        Map<String, List<String>> upstreamHeaders = new java.util.LinkedHashMap<>();
        upstreamHeaders.put("content-type", List.of("text/html"));
        upstreamHeaders.put("X-Other", List.of("val"));
        ServiceResponse<byte[]> upstream = new ServiceResponse<>(
                "hi".getBytes(), 200, upstreamHeaders, "hi".getBytes(), "text/html");
        when(client.proxy(any(), any(), any(), any())).thenReturn(upstream);

        MockHttpServletRequest request = createRequest("GET", "/api/proxy");
        MockHttpServletResponse response = new MockHttpServletResponse();
        LoomRequestHandler handler = passthroughHandler("GET", "/api/proxy", "test-svc", "get-all");

        adapter.handle(request, response, handler);

        assertThat(response.getHeaders("Content-Type")).hasSize(1);
        assertThat(response.getContentType()).isEqualTo("text/html");
    }

    @Test
    void passthroughPath_multiValueResponseHeadersPreserved() throws Exception {
        ServiceClient client = mock(ServiceClient.class);
        when(serviceClientRegistry.getRouteClient("test-svc", "get-all")).thenReturn(client);
        Map<String, List<String>> upstreamHeaders = Map.of(
                "X-Multi", List.of("val1", "val2", "val3")
        );
        ServiceResponse<byte[]> upstream = new ServiceResponse<>(
                "{}".getBytes(), 200, upstreamHeaders, "{}".getBytes(), "application/json");
        when(client.proxy(any(), any(), any(), any())).thenReturn(upstream);

        MockHttpServletRequest request = createRequest("GET", "/api/proxy");
        MockHttpServletResponse response = new MockHttpServletResponse();
        LoomRequestHandler handler = passthroughHandler("GET", "/api/proxy", "test-svc", "get-all");

        adapter.handle(request, response, handler);

        assertThat(response.getHeaders("X-Multi")).containsExactly("val1", "val2", "val3");
    }

    @Test
    void passthroughPath_writesRawBytesWithoutJsonCodec() throws Exception {
        ServiceClient client = mock(ServiceClient.class);
        when(serviceClientRegistry.getRouteClient("test-svc", "get-all")).thenReturn(client);
        byte[] rawBytes = "{\"raw\":true}".getBytes();
        ServiceResponse<byte[]> upstream = new ServiceResponse<>(
                rawBytes, 200, Map.of(), rawBytes, "application/json");
        when(client.proxy(any(), any(), any(), any())).thenReturn(upstream);

        MockHttpServletRequest request = createRequest("GET", "/api/proxy");
        MockHttpServletResponse response = new MockHttpServletResponse();
        LoomRequestHandler handler = passthroughHandler("GET", "/api/proxy", "test-svc", "get-all");

        adapter.handle(request, response, handler);

        // jsonCodec.writeValue() should never be called for passthrough
        verify(jsonCodec, never()).writeValue(any(), any());
        assertThat(response.getContentAsString()).isEqualTo("{\"raw\":true}");
    }

    @Test
    void passthroughPath_interceptorShortCircuitStillUsesJsonPath() throws Exception {
        LoomInterceptor shortCircuitInterceptor = new LoomInterceptor() {
            @Override
            public void handle(LoomHttpContext ctx, InterceptorChain chain) {
                ctx.setResponseBody(Map.of("error", "blocked"));
                ctx.setResponseStatus(403);
            }
        };
        when(interceptorRegistry.getInterceptors(any())).thenReturn(List.of(shortCircuitInterceptor));

        ServiceClient client = mock(ServiceClient.class);
        when(serviceClientRegistry.getRouteClient("test-svc", "get-all")).thenReturn(client);

        MockHttpServletRequest request = createRequest("GET", "/api/proxy");
        MockHttpServletResponse response = new MockHttpServletResponse();
        LoomRequestHandler handler = passthroughHandler("GET", "/api/proxy", "test-svc", "get-all");

        adapter.handle(request, response, handler);

        // proxy() never called because interceptor short-circuited
        verify(client, never()).proxy(any(), any(), any(), any());
        // Falls back to JSON response path
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).isEqualTo("application/json");
    }
}
