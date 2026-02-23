package io.loom.starter.web;

import com.fasterxml.jackson.databind.ObjectMapper;
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

@Slf4j
public class LoomHandlerAdapter implements HandlerAdapter {

    private final DagExecutor dagExecutor;
    private final InterceptorRegistry interceptorRegistry;
    private final ServiceClientRegistry serviceClientRegistry;
    private final ObjectMapper objectMapper;

    public LoomHandlerAdapter(DagExecutor dagExecutor,
                              InterceptorRegistry interceptorRegistry,
                              ServiceClientRegistry serviceClientRegistry,
                              ObjectMapper objectMapper) {
        this.dagExecutor = dagExecutor;
        this.interceptorRegistry = interceptorRegistry;
        this.serviceClientRegistry = serviceClientRegistry;
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

        // Validate request before interceptor chain
        Object cachedBody = null;
        if (api.validationPlan() != null) {
            RequestValidator.ValidationResult vr = RequestValidator.validate(
                    api.validationPlan(), httpContext, objectMapper);
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
            handleBuilder(api, httpContext, pathVars, requestId, cachedBody);
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
                               Map<String, String> pathVars, String requestId,
                               Object cachedBody) {
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
                    serviceClientRegistry,
                    requestId,
                    cachedBody
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

        Runnable serviceCall = () -> {
            ServiceClient client = serviceClientRegistry.getClient(api.serviceName());

            Map<String, String> headers = new LinkedHashMap<>();
            httpContext.getHeaders().forEach((k, v) -> {
                if (!k.equalsIgnoreCase("host") && !k.equalsIgnoreCase("content-length")) {
                    headers.put(k, v.get(0));
                }
            });

            String method = httpContext.getHttpMethod().toUpperCase();
            Object result = switch (method) {
                case "GET" -> client.get(api.servicePath(), Object.class, headers);
                case "POST" -> client.post(api.servicePath(), httpContext.getRawRequestBody(), Object.class, headers);
                case "PUT" -> client.put(api.servicePath(), httpContext.getRawRequestBody(), Object.class, headers);
                case "DELETE" -> client.delete(api.servicePath(), Object.class, headers);
                case "PATCH" -> client.patch(api.servicePath(), httpContext.getRawRequestBody(), Object.class, headers);
                default -> throw new UnsupportedOperationException("Unsupported method: " + method);
            };

            httpContext.setResponseBody(result);
        };

        DefaultInterceptorChain chain = new DefaultInterceptorChain(interceptors, serviceCall);
        chain.next(httpContext);
    }

    @Override
    public long getLastModified(HttpServletRequest request, Object handler) {
        return -1;
    }
}
