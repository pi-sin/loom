package io.loom.benchmark;

import io.loom.core.codec.DslJsonCodec;
import io.loom.core.codec.JsonCodec;
import io.loom.starter.web.LoomHttpContextImpl;

import org.openjdk.jmh.annotations.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class ContextCreationBenchmark {

    private JsonCodec codec;
    private MockHttpServletResponse response;

    @Setup
    public void setup() {
        codec = new DslJsonCodec();
        response = new MockHttpServletResponse();
    }

    private MockHttpServletRequest buildRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/42/orders");
        // 12 headers
        request.addHeader("Accept", "application/json");
        request.addHeader("Content-Type", "application/json");
        request.addHeader("Authorization", "Bearer token-abc-123");
        request.addHeader("X-Request-Id", "req-12345");
        request.addHeader("X-Correlation-Id", "corr-67890");
        request.addHeader("User-Agent", "BenchmarkClient/1.0");
        request.addHeader("Host", "localhost:8080");
        request.addHeader("Connection", "keep-alive");
        request.addHeader("Cache-Control", "no-cache");
        request.addHeader("Accept-Encoding", "gzip, deflate");
        request.addHeader("Accept-Language", "en-US");
        request.addHeader("X-Forwarded-For", "10.0.0.1");
        // 5 query params
        request.addParameter("page", "1");
        request.addParameter("size", "20");
        request.addParameter("sort", "name");
        request.addParameter("filter", "active");
        request.addParameter("fields", "id,name,email");
        return request;
    }

    @Benchmark
    public LoomHttpContextImpl createContextNoHeaderAccess() {
        MockHttpServletRequest request = buildRequest();
        Map<String, String> pathVars = Map.of("userId", "42");
        return new LoomHttpContextImpl(request, response, codec, pathVars, 10_485_760);
    }

    @Benchmark
    public Object createContextWithHeaderAccess() {
        MockHttpServletRequest request = buildRequest();
        Map<String, String> pathVars = Map.of("userId", "42");
        LoomHttpContextImpl ctx = new LoomHttpContextImpl(request, response, codec, pathVars, 10_485_760);
        return ctx.getHeaders();
    }

    @Benchmark
    public Object createContextWithQueryParamAccess() {
        MockHttpServletRequest request = buildRequest();
        Map<String, String> pathVars = Map.of("userId", "42");
        LoomHttpContextImpl ctx = new LoomHttpContextImpl(request, response, codec, pathVars, 10_485_760);
        return ctx.getQueryParams();
    }

    @Benchmark
    public String singleHeaderAccess() {
        MockHttpServletRequest request = buildRequest();
        Map<String, String> pathVars = Map.of("userId", "42");
        LoomHttpContextImpl ctx = new LoomHttpContextImpl(request, response, codec, pathVars, 10_485_760);
        return ctx.getHeader("Authorization");
    }
}
