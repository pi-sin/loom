package io.loom.starter.web;

import io.loom.core.codec.JsonCodec;
import io.loom.core.engine.DagExecutor;
import io.loom.core.exception.LoomException;
import io.loom.core.interceptor.LoomInterceptor;
import io.loom.core.model.ApiDefinition;
import io.loom.core.service.ServiceClient;
import io.loom.core.service.ServiceResponse;
import io.loom.core.validation.RequestValidator;
import io.loom.starter.context.SpringBuilderContext;
import io.loom.starter.registry.DefaultInterceptorChain;
import io.loom.starter.registry.InterceptorRegistry;
import io.loom.starter.service.ServiceClientRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.HandlerAdapter;
import org.springframework.web.servlet.ModelAndView;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Locale.ROOT;

@Slf4j
public class LoomHandlerAdapter implements HandlerAdapter {

    // RFC 2616 §13.5.1 — hop-by-hop headers that proxies MUST NOT forward
    private static final Set<String> HOP_BY_HOP_HEADERS = Set.of(
            "host", "content-length", "connection", "keep-alive",
            "transfer-encoding", "te", "trailer", "upgrade",
            "proxy-authenticate", "proxy-authorization"
    );

    private final DagExecutor dagExecutor;
    private final InterceptorRegistry interceptorRegistry;
    private final ServiceClientRegistry serviceClientRegistry;
    private final JsonCodec jsonCodec;
    private final long maxRequestBodySize;

    public LoomHandlerAdapter(DagExecutor dagExecutor,
                              InterceptorRegistry interceptorRegistry,
                              ServiceClientRegistry serviceClientRegistry,
                              JsonCodec jsonCodec,
                              long maxRequestBodySize) {
        this.dagExecutor = dagExecutor;
        this.interceptorRegistry = interceptorRegistry;
        this.serviceClientRegistry = serviceClientRegistry;
        this.jsonCodec = jsonCodec;
        this.maxRequestBodySize = maxRequestBodySize;
    }

    @Override
    public boolean supports(Object handler) {
        return handler instanceof LoomRequestHandler;
    }

    @Override
    public ModelAndView handle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        LoomRequestHandler loomHandler = (LoomRequestHandler) handler;
        Map<String, String> pathVars = loomHandler.getPathVariables();

        LoomHttpContextImpl httpContext = new LoomHttpContextImpl(
                request, response, jsonCodec, pathVars, maxRequestBodySize);

        ApiDefinition api = loomHandler.getApiDefinition();

        // Validate request before interceptor chain
        Object cachedBody = null;
        if (api.validationPlan() != null) {
            RequestValidator.ValidationResult vr = RequestValidator.validate(
                    api.validationPlan(), httpContext, jsonCodec);
            if (vr != null) {
                if (vr.queryParamDefaults() != null) httpContext.applyQueryParamDefaults(vr.queryParamDefaults());
                if (vr.parsedBody() != null) {
                    httpContext.cacheParsedBody(vr.parsedBody());
                    cachedBody = vr.parsedBody();
                }
            }
        }

        if (api.isPassthrough()) {
            ServiceResponse<byte[]> upstream = handlePassthrough(api, httpContext);
            if (upstream != null) {
                writeProxyResponse(response, upstream);
            } else {
                // Interceptor short-circuited — fall back to JSON response path
                writeJsonResponse(response, httpContext);
            }
        } else {
            handleBuilder(api, httpContext, pathVars, cachedBody);
            writeJsonResponse(response, httpContext);
        }

        return null;
    }

    private void handleBuilder(ApiDefinition api, LoomHttpContextImpl httpContext,
                               Map<String, String> pathVars,
                               Object cachedBody) {
        // Build interceptor chain
        List<LoomInterceptor> interceptors = interceptorRegistry.getInterceptors(api.interceptors());

        var resultHolder = new AtomicReference<>();

        Runnable dagExecution = () -> {
            SpringBuilderContext builderContext = new SpringBuilderContext(
                    httpContext.getHttpMethod(),
                    httpContext.getRequestPath(),
                    pathVars,
                    httpContext.getQueryParams(),
                    httpContext.getHeaders(),
                    httpContext.getRawRequestBody(),
                    jsonCodec,
                    serviceClientRegistry,
                    cachedBody
            );

            // Copy attributes from interceptors to builder context
            httpContext.getAttributes().forEach(builderContext::setAttribute);

            try {
                resultHolder.set(dagExecutor.execute(api.dag(), builderContext));
            } catch (LoomException ex) {
                throw (LoomException) ex.withApiRoute(api.method() + " " + api.path());
            } catch (Exception ex) {
                throw (LoomException) new LoomException("Builder execution failed", ex)
                        .withApiRoute(api.method() + " " + api.path());
            }
        };

        DefaultInterceptorChain chain = new DefaultInterceptorChain(interceptors, dagExecution);
        chain.next(httpContext);

        Object dagResult = resultHolder.get();
        if (dagResult != null) {
            httpContext.setResponseBody(dagResult);
        }
    }

    /**
     * Executes passthrough proxy via {@code ServiceClient.proxy()}.
     * Returns the upstream {@link ServiceResponse} if the proxy call completed,
     * or {@code null} if an interceptor short-circuited the chain.
     */
    private ServiceResponse<byte[]> handlePassthrough(ApiDefinition api, LoomHttpContextImpl httpContext) {
        List<LoomInterceptor> interceptors = interceptorRegistry.getInterceptors(api.interceptors());

        var upstreamHolder = new AtomicReference<ServiceResponse<byte[]>>();

        Runnable serviceCall = () -> {
            try {
                ServiceClient client = serviceClientRegistry.getRouteClient(api.serviceName(), api.serviceRoute());

                Map<String, String> headers = new LinkedHashMap<>();
                httpContext.getHeaders().forEach((k, v) -> {
                    if (!HOP_BY_HOP_HEADERS.contains(k.toLowerCase(ROOT))) {
                        headers.put(k, v.size() == 1 ? v.get(0) : String.join(", ", v));
                    }
                });

                String resolvedPath = api.servicePathTemplate().resolve(
                        httpContext.getPathVariablesRaw(), httpContext.getQueryString());

                String method = httpContext.getHttpMethod().toUpperCase();
                byte[] requestBody = switch (method) {
                    case "POST", "PUT", "PATCH" -> httpContext.getRawRequestBody();
                    default -> null;
                };

                ServiceResponse<byte[]> upstream = client.proxy(method, resolvedPath, requestBody, headers);
                upstreamHolder.set(upstream);
            } catch (LoomException ex) {
                throw (LoomException) ex.withApiRoute(api.method() + " " + api.path());
            } catch (Exception ex) {
                throw (LoomException) new LoomException("Proxy call failed", ex)
                        .withApiRoute(api.method() + " " + api.path());
            }
        };

        DefaultInterceptorChain chain = new DefaultInterceptorChain(interceptors, serviceCall);
        chain.next(httpContext);

        return upstreamHolder.get();
    }

    private void writeProxyResponse(HttpServletResponse response, ServiceResponse<byte[]> upstream) throws Exception {
        response.setStatus(upstream.statusCode());

        // Forward upstream response headers, filtering hop-by-hop and content-type (set explicitly below)
        if (upstream.headers() != null) {
            upstream.headers().forEach((name, values) -> {
                String lower = name.toLowerCase(ROOT);
                if (!HOP_BY_HOP_HEADERS.contains(lower) && !"content-type".equals(lower)) {
                    for (String value : values) {
                        response.addHeader(name, value);
                    }
                }
            });
        }

        response.setContentType(upstream.contentType());

        if (upstream.rawBody() != null && upstream.rawBody().length > 0) {
            response.getOutputStream().write(upstream.rawBody());
        }
    }

    private void writeJsonResponse(HttpServletResponse response, LoomHttpContextImpl httpContext) throws Exception {
        response.setStatus(httpContext.getResponseStatus());
        response.setContentType("application/json");

        Object responseBody = httpContext.getResponseBody();
        if (responseBody != null) {
            jsonCodec.writeValue(response.getOutputStream(), responseBody);
        }
    }

    @Override
    public long getLastModified(HttpServletRequest request, Object handler) {
        return -1;
    }
}
