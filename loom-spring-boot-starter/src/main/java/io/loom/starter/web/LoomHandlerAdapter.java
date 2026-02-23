package io.loom.starter.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.loom.core.engine.DagExecutor;
import io.loom.core.exception.LoomException;
import io.loom.core.interceptor.LoomInterceptor;
import io.loom.core.model.ApiDefinition;
import io.loom.core.upstream.UpstreamClient;
import io.loom.starter.context.SpringBuilderContext;
import io.loom.starter.registry.DefaultInterceptorChain;
import io.loom.starter.registry.InterceptorRegistry;
import io.loom.starter.upstream.UpstreamClientRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.HandlerAdapter;
import org.springframework.web.servlet.ModelAndView;

import java.util.*;

@Slf4j
public class LoomHandlerAdapter implements HandlerAdapter {

    private final DagExecutor dagExecutor;
    private final InterceptorRegistry interceptorRegistry;
    private final UpstreamClientRegistry upstreamClientRegistry;
    private final ObjectMapper objectMapper;

    public LoomHandlerAdapter(DagExecutor dagExecutor,
                              InterceptorRegistry interceptorRegistry,
                              UpstreamClientRegistry upstreamClientRegistry,
                              ObjectMapper objectMapper) {
        this.dagExecutor = dagExecutor;
        this.interceptorRegistry = interceptorRegistry;
        this.upstreamClientRegistry = upstreamClientRegistry;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(Object handler) {
        return handler instanceof LoomRequestHandler;
    }

    @Override
    public ModelAndView handle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        LoomRequestHandler loomHandler = (LoomRequestHandler) handler;
        Map<String, String> pathVars = loomHandler.getPathVariables();
        String requestId = UUID.randomUUID().toString();

        LoomHttpContextImpl httpContext = new LoomHttpContextImpl(
                request, response, objectMapper, pathVars, requestId);

        ApiDefinition api = loomHandler.getApiDefinition();

        if (api.isPassthrough()) {
            handlePassthrough(api, httpContext);
        } else {
            handleBuilder(api, httpContext, pathVars, requestId);
        }

        response.setStatus(httpContext.getResponseStatus());
        response.setContentType("application/json");

        Object responseBody = httpContext.getResponseBody();
        if (responseBody != null) {
            objectMapper.writeValue(response.getOutputStream(), responseBody);
        }

        return null;
    }

    private void handleBuilder(ApiDefinition api, LoomHttpContextImpl httpContext,
                               Map<String, String> pathVars, String requestId) {
        // Build interceptor chain
        List<LoomInterceptor> interceptors = interceptorRegistry.getInterceptors(api.interceptors());

        final Object[] result = new Object[1];

        Runnable dagExecution = () -> {
            SpringBuilderContext builderContext = new SpringBuilderContext(
                    httpContext.getHttpMethod(),
                    httpContext.getRequestPath(),
                    pathVars,
                    httpContext.getQueryParams(),
                    httpContext.getHeaders(),
                    httpContext.getRawRequestBody(),
                    objectMapper,
                    upstreamClientRegistry,
                    requestId
            );

            // Copy attributes from interceptors to builder context
            httpContext.getAttributes().forEach(builderContext::setAttribute);

            try {
                result[0] = dagExecutor.execute(api.dag(), builderContext);
            } catch (LoomException ex) {
                throw (LoomException) ex.withRequestId(requestId)
                        .withApiRoute(api.method() + " " + api.path());
            }
        };

        DefaultInterceptorChain chain = new DefaultInterceptorChain(interceptors, dagExecution);
        chain.next(httpContext);

        httpContext.setResponseBody(result[0]);
    }

    private void handlePassthrough(ApiDefinition api, LoomHttpContextImpl httpContext) {
        List<LoomInterceptor> interceptors = interceptorRegistry.getInterceptors(api.interceptors());

        Runnable upstreamCall = () -> {
            UpstreamClient client = upstreamClientRegistry.getClient(api.upstreamName());

            Map<String, String> headers = new LinkedHashMap<>();
            httpContext.getHeaders().forEach((k, v) -> {
                if (!k.equalsIgnoreCase("host") && !k.equalsIgnoreCase("content-length")) {
                    headers.put(k, v.get(0));
                }
            });

            String method = httpContext.getHttpMethod().toUpperCase();
            Object result = switch (method) {
                case "GET" -> client.get(api.upstreamPath(), Object.class, headers);
                case "POST" -> client.post(api.upstreamPath(), httpContext.getRawRequestBody(), Object.class, headers);
                case "PUT" -> client.put(api.upstreamPath(), httpContext.getRawRequestBody(), Object.class, headers);
                case "DELETE" -> client.delete(api.upstreamPath(), Object.class, headers);
                case "PATCH" -> client.patch(api.upstreamPath(), httpContext.getRawRequestBody(), Object.class, headers);
                default -> throw new UnsupportedOperationException("Unsupported method: " + method);
            };

            httpContext.setResponseBody(result);
        };

        DefaultInterceptorChain chain = new DefaultInterceptorChain(interceptors, upstreamCall);
        chain.next(httpContext);
    }

    @Override
    public long getLastModified(HttpServletRequest request, Object handler) {
        return -1;
    }
}
