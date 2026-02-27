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
        when(client.get(any(), eq(Object.class), any())).thenThrow(original);

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
        when(client.get(any(), eq(Object.class), any())).thenThrow(new RuntimeException("connection reset"));

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
        when(client.get(any(), eq(Object.class), any())).thenReturn(Map.of("status", "ok"));

        MockHttpServletRequest request = createRequest("GET", "/api/proxy");
        request.addHeader("X-Custom", "single-value");
        MockHttpServletResponse response = new MockHttpServletResponse();
        LoomRequestHandler handler = passthroughHandler("GET", "/api/proxy", "test-svc", "get-all");

        adapter.handle(request, response, handler);

        @SuppressWarnings("unchecked")
        var headersCaptor = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(client).get(any(), eq(Object.class), headersCaptor.capture());

        @SuppressWarnings("unchecked")
        Map<String, String> forwardedHeaders = headersCaptor.getValue();
        assertThat(forwardedHeaders.get("X-Custom")).isEqualTo("single-value");
    }

    @Test
    void passthroughPath_hopByHopHeadersFiltered() throws Exception {
        ServiceClient client = mock(ServiceClient.class);
        when(serviceClientRegistry.getRouteClient("test-svc", "get-all")).thenReturn(client);
        when(client.get(any(), eq(Object.class), any())).thenReturn(Map.of("status", "ok"));

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
        verify(client).get(any(), eq(Object.class), headersCaptor.capture());

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
}
