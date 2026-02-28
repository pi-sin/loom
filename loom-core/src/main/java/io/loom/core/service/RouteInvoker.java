package io.loom.core.service;

/**
 * Fluent interface for invoking an upstream service route.
 * Auto-forwards path variables and query params from the incoming request;
 * explicit overrides take precedence.
 */
public interface RouteInvoker {

    RouteInvoker pathVar(String name, String value);

    RouteInvoker queryParam(String name, String value);

    RouteInvoker header(String name, String value);

    RouteInvoker body(Object body);

    <T> T get(Class<T> responseType);

    <T> T post(Class<T> responseType);

    <T> T put(Class<T> responseType);

    <T> T delete(Class<T> responseType);

    <T> T patch(Class<T> responseType);

    /** Returns full response metadata without throwing on 4xx/5xx. */
    <T> ServiceResponse<T> getResponse(Class<T> responseType);

    /** Returns full response metadata without throwing on 4xx/5xx. */
    <T> ServiceResponse<T> postResponse(Class<T> responseType);

    /** Returns full response metadata without throwing on 4xx/5xx. */
    <T> ServiceResponse<T> putResponse(Class<T> responseType);

    /** Returns full response metadata without throwing on 4xx/5xx. */
    <T> ServiceResponse<T> deleteResponse(Class<T> responseType);

    /** Returns full response metadata without throwing on 4xx/5xx. */
    <T> ServiceResponse<T> patchResponse(Class<T> responseType);
}
