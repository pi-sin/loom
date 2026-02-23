package io.loom.core.interceptor;

import java.util.List;
import java.util.Map;

public interface LoomHttpContext {
    String getHttpMethod();
    String getRequestPath();
    String getHeader(String name);
    Map<String, List<String>> getHeaders();
    String getQueryParam(String name);
    Map<String, List<String>> getQueryParams();
    String getPathVariable(String name);
    Map<String, String> getPathVariables();
    byte[] getRawRequestBody();
    <T> T getRequestBody(Class<T> type);

    void setAttribute(String key, Object value);
    <T> T getAttribute(String key, Class<T> type);
    Map<String, Object> getAttributes();

    void setResponseStatus(int status);
    void setResponseHeader(String name, String value);
    void setResponseBody(Object body);
    int getResponseStatus();
    Object getResponseBody();

    String getRequestId();
}
