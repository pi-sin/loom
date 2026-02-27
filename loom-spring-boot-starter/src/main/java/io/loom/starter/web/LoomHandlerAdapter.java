package io.loom.starter.web;

import io.loom.core.codec.JsonCodec;
import io.loom.core.engine.DagExecutor;
import io.loom.core.exception.LoomException;
import io.loom.core.interceptor.LoomInterceptor;
import io.loom.core.model.ApiDefinition;
import io.loom.core.service.ServiceClient;
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
            handlePassthrough(api, httpContext);
        } else {
            handleBuilder(api, httpContext, pathVars, cachedBody);
        }

        response.setStatus(httpContext.getResponseStatus());
        response.setContentType("application/json");

        Object responseBody = httpContext.getResponseBody();
        if (responseBody != null) {
            jsonCodec.writeValue(response.getOutputStream(), responseBody);
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

    private void handlePassthrough(ApiDefinition api, LoomHttpContextImpl httpContext) {
        List<LoomInterceptor> interceptors = interceptorRegistry.getInterceptors(api.interceptors());

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
                Object result = switch (method) {
                    case "GET" -> client.get(resolvedPath, Object.class, headers);
                    case "POST" -> client.post(resolvedPath, httpContext.getRawRequestBody(), Object.class, headers);
                    case "PUT" -> client.put(resolvedPath, httpContext.getRawRequestBody(), Object.class, headers);
                    case "DELETE" -> client.delete(resolvedPath, Object.class, headers);
                    case "PATCH" -> client.patch(resolvedPath, httpContext.getRawRequestBody(), Object.class, headers);
                    default -> throw new UnsupportedOperationException("Unsupported method: " + method);
                };

                httpContext.setResponseBody(result);
            } catch (LoomException ex) {
                throw (LoomException) ex.withApiRoute(api.method() + " " + api.path());
            } catch (Exception ex) {
                throw (LoomException) new LoomException("Proxy call failed", ex)
                        .withApiRoute(api.method() + " " + api.path());
            }
        };

        DefaultInterceptorChain chain = new DefaultInterceptorChain(interceptors, serviceCall);
        chain.next(httpContext);
    }

    @Override
    public long getLastModified(HttpServletRequest request, Object handler) {
        return -1;
    }
}
