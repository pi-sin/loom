package io.loom.starter.web;

import io.loom.core.codec.JsonCodec;
import io.loom.core.exception.LoomException;
import io.loom.core.interceptor.LoomHttpContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class LoomHttpContextImpl implements LoomHttpContext {

    private final HttpServletRequest request;
    private final HttpServletResponse response;
    private final JsonCodec jsonCodec;
    private final Map<String, String> pathVariables;
    private final String requestId;
    private final byte[] rawBody;

    private final Map<String, List<String>> cachedHeaders;

    private final ConcurrentHashMap<String, Object> attributes = new ConcurrentHashMap<>();
    private Object responseBody;
    private int responseStatus = 200;
    private Map<String, String> queryParamDefaults;
    private Map<String, List<String>> cachedQueryParams;
    private Object cachedParsedBody;

    public LoomHttpContextImpl(HttpServletRequest request, HttpServletResponse response,
                               JsonCodec jsonCodec, Map<String, String> pathVariables,
                               String requestId) {
        this.request = request;
        this.response = response;
        this.jsonCodec = jsonCodec;
        this.pathVariables = pathVariables != null ? pathVariables : Map.of();
        this.requestId = requestId;
        this.rawBody = readBody(request);
        this.cachedHeaders = buildHeaders(request);
    }

    private static Map<String, List<String>> buildHeaders(HttpServletRequest request) {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        Enumeration<String> names = request.getHeaderNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            headers.put(name, Collections.list(request.getHeaders(name)));
        }
        return Collections.unmodifiableMap(headers);
    }

    private byte[] readBody(HttpServletRequest request) {
        try {
            return request.getInputStream().readAllBytes();
        } catch (IOException e) {
            return new byte[0];
        }
    }

    @Override
    public String getHttpMethod() {
        return request.getMethod();
    }

    @Override
    public String getRequestPath() {
        return request.getRequestURI();
    }

    @Override
    public String getHeader(String name) {
        return request.getHeader(name);
    }

    @Override
    public Map<String, List<String>> getHeaders() {
        return cachedHeaders;
    }

    @Override
    public String getQueryParam(String name) {
        String val = request.getParameter(name);
        if (val == null && queryParamDefaults != null) {
            val = queryParamDefaults.get(name);
        }
        return val;
    }

    @Override
    public Map<String, List<String>> getQueryParams() {
        if (cachedQueryParams != null) {
            return cachedQueryParams;
        }
        cachedQueryParams = buildQueryParams();
        return cachedQueryParams;
    }

    private Map<String, List<String>> buildQueryParams() {
        Map<String, List<String>> params = new LinkedHashMap<>();
        request.getParameterMap().forEach((k, v) -> params.put(k, Arrays.asList(v)));
        if (queryParamDefaults != null) {
            queryParamDefaults.forEach((k, v) -> params.putIfAbsent(k, List.of(v)));
        }
        return Collections.unmodifiableMap(params);
    }

    @Override
    public String getPathVariable(String name) {
        return pathVariables.get(name);
    }

    @Override
    public Map<String, String> getPathVariables() {
        return Collections.unmodifiableMap(pathVariables);
    }

    Map<String, String> getPathVariablesRaw() {
        return pathVariables;
    }

    @Override
    public String getQueryString() {
        return request.getQueryString();
    }

    @Override
    public byte[] getRawRequestBody() {
        return rawBody;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getRequestBody(Class<T> type) {
        if (cachedParsedBody != null && type.isInstance(cachedParsedBody)) {
            return (T) cachedParsedBody;
        }
        if (rawBody == null || rawBody.length == 0) {
            return null;
        }
        try {
            return jsonCodec.readValue(rawBody, type);
        } catch (Exception e) {
            throw new LoomException("Failed to deserialize request body", e);
        }
    }

    void applyQueryParamDefaults(Map<String, String> defaults) {
        this.queryParamDefaults = defaults;
        this.cachedQueryParams = null; // invalidate cache
    }

    void cacheParsedBody(Object body) {
        this.cachedParsedBody = body;
    }

    @Override
    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key, Class<T> type) {
        return (T) attributes.get(key);
    }

    @Override
    public Map<String, Object> getAttributes() {
        return Collections.unmodifiableMap(attributes);
    }

    @Override
    public void setResponseStatus(int status) {
        this.responseStatus = status;
    }

    @Override
    public void setResponseHeader(String name, String value) {
        response.setHeader(name, value);
    }

    @Override
    public void setResponseBody(Object body) {
        this.responseBody = body;
    }

    @Override
    public int getResponseStatus() {
        return responseStatus;
    }

    @Override
    public Object getResponseBody() {
        return responseBody;
    }

    @Override
    public String getRequestId() {
        return requestId;
    }

    public HttpServletRequest getServletRequest() {
        return request;
    }

    public HttpServletResponse getServletResponse() {
        return response;
    }
}
