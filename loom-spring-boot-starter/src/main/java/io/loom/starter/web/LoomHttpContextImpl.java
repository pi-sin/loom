package io.loom.starter.web;

import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ObjectMapper objectMapper;
    private final Map<String, String> pathVariables;
    private final String requestId;
    private final byte[] rawBody;

    private final ConcurrentHashMap<String, Object> attributes = new ConcurrentHashMap<>();
    private Object responseBody;
    private int responseStatus = 200;

    public LoomHttpContextImpl(HttpServletRequest request, HttpServletResponse response,
                               ObjectMapper objectMapper, Map<String, String> pathVariables,
                               String requestId) {
        this.request = request;
        this.response = response;
        this.objectMapper = objectMapper;
        this.pathVariables = pathVariables != null ? pathVariables : Map.of();
        this.requestId = requestId;
        this.rawBody = readBody(request);
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
        Map<String, List<String>> headers = new LinkedHashMap<>();
        Enumeration<String> names = request.getHeaderNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            headers.put(name, Collections.list(request.getHeaders(name)));
        }
        return headers;
    }

    @Override
    public String getQueryParam(String name) {
        return request.getParameter(name);
    }

    @Override
    public Map<String, List<String>> getQueryParams() {
        Map<String, List<String>> params = new LinkedHashMap<>();
        request.getParameterMap().forEach((k, v) -> params.put(k, Arrays.asList(v)));
        return params;
    }

    @Override
    public String getPathVariable(String name) {
        return pathVariables.get(name);
    }

    @Override
    public Map<String, String> getPathVariables() {
        return Collections.unmodifiableMap(pathVariables);
    }

    @Override
    public byte[] getRawRequestBody() {
        return rawBody;
    }

    @Override
    public <T> T getRequestBody(Class<T> type) {
        if (rawBody == null || rawBody.length == 0) {
            return null;
        }
        try {
            return objectMapper.readValue(rawBody, type);
        } catch (Exception e) {
            throw new LoomException("Failed to deserialize request body", e);
        }
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
