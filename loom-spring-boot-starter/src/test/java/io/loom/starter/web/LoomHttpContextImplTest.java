package io.loom.starter.web;

import io.loom.core.codec.JsonCodec;
import io.loom.core.exception.LoomException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LoomHttpContextImplTest {

    private final JsonCodec jsonCodec = mock(JsonCodec.class);
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @Test
    void rejectsBodyExceedingMaxSizeViaContentLength() throws Exception {
        // MockHttpServletRequest doesn't respect Content-Length header for getContentLengthLong(),
        // so we use a Mockito mock to simulate a request with a large Content-Length
        HttpServletRequest mockRequest = mock(HttpServletRequest.class);
        when(mockRequest.getMethod()).thenReturn("POST");
        when(mockRequest.getContentLengthLong()).thenReturn(2000L);

        assertThatThrownBy(() -> new LoomHttpContextImpl(mockRequest, response, jsonCodec, null, 1024))
                .isInstanceOf(LoomException.class)
                .hasMessageContaining("Request body too large")
                .hasMessageContaining("2000")
                .hasMessageContaining("1024");
    }

    @Test
    void rejectsBodyExceedingMaxSizeAfterRead() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/test");
        // Content-Length not set (defaults to -1), but actual body exceeds limit
        request.setContent(new byte[2000]);

        assertThatThrownBy(() -> new LoomHttpContextImpl(request, response, jsonCodec, null, 1024))
                .isInstanceOf(LoomException.class)
                .hasMessageContaining("Request body too large")
                .hasMessageContaining("1024");
    }

    @Test
    void acceptsBodyWithinLimit() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/test");
        byte[] body = new byte[1024];
        request.setContent(body);

        LoomHttpContextImpl ctx = new LoomHttpContextImpl(request, response, jsonCodec, null, 1024);

        assertThat(ctx.getRawRequestBody()).hasSize(1024);
    }

    @Test
    void rejectsChunkedBodyExceedingMaxSize() {
        // Chunked transfer: Content-Length is -1 (unknown), but actual body exceeds limit
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/test");
        request.setContent(new byte[2000]);

        assertThatThrownBy(() -> new LoomHttpContextImpl(request, response, jsonCodec, null, 1024))
                .isInstanceOf(LoomException.class)
                .hasMessageContaining("Request body too large")
                .hasMessageContaining("1024");
    }

    @Test
    void skipsBodyReadForGetRequests() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/test");
        // Don't set any content — GET requests should not read body

        LoomHttpContextImpl ctx = new LoomHttpContextImpl(request, response, jsonCodec, null, 1024);

        assertThat(ctx.getRawRequestBody()).isEmpty();
    }

    @Test
    void skipsBodyReadForDeleteRequests() {
        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/test");

        LoomHttpContextImpl ctx = new LoomHttpContextImpl(request, response, jsonCodec, null, 1024);

        assertThat(ctx.getRawRequestBody()).isEmpty();
    }

    @Test
    void throwsOnIOExceptionDuringRead() throws Exception {
        HttpServletRequest mockRequest = mock(HttpServletRequest.class);
        when(mockRequest.getContentLengthLong()).thenReturn(-1L);
        when(mockRequest.getMethod()).thenReturn("POST");

        ServletInputStream sis = mock(ServletInputStream.class);
        when(sis.readNBytes(anyInt())).thenThrow(new IOException("disk error"));
        when(mockRequest.getInputStream()).thenReturn(sis);

        assertThatThrownBy(() -> new LoomHttpContextImpl(mockRequest, response, jsonCodec, null, 10_485_760))
                .isInstanceOf(LoomException.class)
                .hasMessageContaining("Failed to read request body")
                .hasCauseInstanceOf(IOException.class);
    }

    @Test
    void singleValueHeaderUsesImmutableSingleton() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Request-Id", "abc-123");
        request.setContent(new byte[0]);

        LoomHttpContextImpl ctx = new LoomHttpContextImpl(request, response, jsonCodec, null, 10_485_760);

        List<String> values = ctx.getHeaders().get("X-Request-Id");
        assertThat(values).containsExactly("abc-123");
        // List.of() returns an immutable list
        assertThatThrownBy(() -> values.add("extra"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void multiValueHeaderUsesImmutableList() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Accept", "application/json");
        request.addHeader("Accept", "text/plain");
        request.setContent(new byte[0]);

        LoomHttpContextImpl ctx = new LoomHttpContextImpl(request, response, jsonCodec, null, 10_485_760);

        List<String> values = ctx.getHeaders().get("Accept");
        assertThat(values).containsExactly("application/json", "text/plain");
        // List.copyOf() returns an immutable list
        assertThatThrownBy(() -> values.add("extra"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void headersMapIsUnmodifiable() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Custom", "value");
        request.setContent(new byte[0]);

        LoomHttpContextImpl ctx = new LoomHttpContextImpl(request, response, jsonCodec, null, 10_485_760);

        Map<String, List<String>> headers = ctx.getHeaders();
        assertThatThrownBy(() -> headers.put("injected", List.of("bad")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void noGetRequestIdMethod() {
        try {
            LoomHttpContextImpl.class.getMethod("getRequestId");
            throw new AssertionError("getRequestId() method should not exist on LoomHttpContextImpl");
        } catch (NoSuchMethodException expected) {
            // Correct — method was removed
        }
    }

    @Test
    void constructorDoesNotRequireRequestId() throws Exception {
        // Verify the constructor accepts exactly 5 parameters (no requestId)
        var constructor = LoomHttpContextImpl.class.getConstructor(
                jakarta.servlet.http.HttpServletRequest.class,
                jakarta.servlet.http.HttpServletResponse.class,
                JsonCodec.class,
                Map.class,
                long.class
        );
        assertThat(constructor.getParameterCount()).isEqualTo(5);
    }
}
