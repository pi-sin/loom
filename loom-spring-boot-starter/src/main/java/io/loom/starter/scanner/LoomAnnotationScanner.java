package io.loom.starter.scanner;

import io.loom.core.annotation.LoomApi;
import io.loom.core.annotation.LoomGraph;
import io.loom.core.annotation.LoomProxy;
import io.loom.core.engine.Dag;
import io.loom.core.engine.DagCompiler;
import io.loom.core.model.ApiDefinition;
import io.loom.core.model.HeaderParamDefinition;
import io.loom.core.model.QueryParamDefinition;
import io.loom.core.registry.ApiRegistry;
import io.loom.core.service.RouteConfig;
import io.loom.core.validation.RequestValidator;
import io.loom.core.validation.ValidationPlan;
import io.loom.starter.service.ServiceClientRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Slf4j
public class LoomAnnotationScanner {

    private final ApplicationContext applicationContext;
    private final DagCompiler dagCompiler;
    private final ApiRegistry apiRegistry;
    private final ServiceClientRegistry serviceClientRegistry;

    public LoomAnnotationScanner(ApplicationContext applicationContext,
                                  DagCompiler dagCompiler,
                                  ApiRegistry apiRegistry,
                                  ServiceClientRegistry serviceClientRegistry) {
        this.applicationContext = applicationContext;
        this.dagCompiler = dagCompiler;
        this.apiRegistry = apiRegistry;
        this.serviceClientRegistry = serviceClientRegistry;
    }

    public void scan() {
        scanApis();
    }

    private void scanApis() {
        Map<String, Object> beans = applicationContext.getBeansWithAnnotation(LoomApi.class);

        for (Map.Entry<String, Object> entry : beans.entrySet()) {
            Class<?> apiClass = org.springframework.aop.support.AopUtils.getTargetClass(entry.getValue());
            LoomApi api = apiClass.getAnnotation(LoomApi.class);
            LoomGraph graph = apiClass.getAnnotation(LoomGraph.class);
            LoomProxy proxy = apiClass.getAnnotation(LoomProxy.class);

            if (api == null) continue;

            List<QueryParamDefinition> queryParams = Arrays.stream(api.queryParams())
                    .map(qp -> new QueryParamDefinition(
                            qp.name(), qp.type(), qp.required(),
                            qp.defaultValue(), qp.description()))
                    .toList();

            List<HeaderParamDefinition> headerParams = Arrays.stream(api.headers())
                    .map(hp -> new HeaderParamDefinition(
                            hp.name(), hp.required(), hp.description()))
                    .toList();

            if (graph != null) {
                Dag dag = dagCompiler.compile(apiClass);
                ValidationPlan validationPlan = RequestValidator.compile(
                        queryParams, headerParams, api.request(), api.method());

                ApiDefinition definition = new ApiDefinition(
                        api.method(),
                        api.path(),
                        api.request(),
                        api.response(),
                        api.interceptors(),
                        dag,
                        api.summary(),
                        api.description(),
                        api.tags(),
                        queryParams,
                        headerParams,
                        null,
                        null,
                        null,
                        validationPlan
                );
                apiRegistry.registerApi(definition);
                log.info("[Loom] Scanned builder API: {} {} from {}",
                        api.method(), api.path(), apiClass.getSimpleName());
            } else if (proxy != null) {
                ValidationPlan validationPlan = RequestValidator.compile(
                        queryParams, headerParams, api.request(), api.method());

                RouteConfig routeConfig = serviceClientRegistry.getRouteConfig(
                        proxy.service(), proxy.route());

                ApiDefinition definition = new ApiDefinition(
                        api.method(),
                        api.path(),
                        api.request(),
                        api.response(),
                        api.interceptors(),
                        null,
                        api.summary(),
                        api.description(),
                        api.tags(),
                        queryParams,
                        headerParams,
                        proxy.service(),
                        proxy.route(),
                        routeConfig.compiledTemplate(),
                        validationPlan
                );
                apiRegistry.registerApi(definition);
                log.info("[Loom] Scanned passthrough API: {} {} -> {}.{} from {}",
                        api.method(), api.path(), proxy.service(), proxy.route(),
                        apiClass.getSimpleName());
            } else {
                log.warn("[Loom] Skipping @LoomApi class {} — missing @LoomGraph or @LoomProxy",
                        apiClass.getSimpleName());
            }
        }
    }
}
