package io.loom.core.interceptor;

/**
 * Marker sub-interface for interceptors that should run on every request.
 *
 * <p>Interceptors implementing {@code LoomGlobalInterceptor} are automatically applied
 * to all APIs. Interceptors implementing only {@link LoomInterceptor} are per-API and
 * only execute when explicitly referenced via {@code @LoomApi(interceptors = {...})}.
 */
public interface LoomGlobalInterceptor extends LoomInterceptor {
}
