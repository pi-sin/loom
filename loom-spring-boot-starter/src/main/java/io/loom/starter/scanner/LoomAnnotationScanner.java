package io.loom.starter.scanner;

import io.loom.core.annotation.LoomApi;
import io.loom.core.annotation.LoomGraph;
import io.loom.core.annotation.LoomHeaderParam;
import io.loom.core.annotation.LoomPassthrough;
import io.loom.core.annotation.LoomQueryParam;
import io.loom.core.engine.Dag;
import io.loom.core.engine.DagCompiler;
import io.loom.core.model.ApiDefinition;
import io.loom.core.model.HeaderParamDefinition;
import io.loom.core.model.PassthroughDefinition;
import io.loom.core.model.QueryParamDefinition;
import io.loom.core.registry.ApiRegistry;
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

    public LoomAnnotationScanner(ApplicationContext applicationContext,
                                  DagCompiler dagCompiler,
                                  ApiRegistry apiRegistry) {
        this.applicationContext = applicationContext;
        this.dagCompiler = dagCompiler;
        this.apiRegistry = apiRegistry;
    }

    public void scan() {
        scanBuilderApis();
        scanPassthroughApis();
    }

    private void scanBuilderApis() {
        Map<String, Object> beans = applicationContext.getBeansWithAnnotation(LoomApi.class);

        for (Map.Entry<String, Object> entry : beans.entrySet()) {
            Class<?> apiClass = entry.getValue().getClass();
            LoomApi api = apiClass.getAnnotation(LoomApi.class);
            LoomGraph graph = apiClass.getAnnotation(LoomGraph.class);

            if (api == null) continue;

            if (graph != null) {
                Dag dag = dagCompiler.compile(apiClass);

                List<QueryParamDefinition> queryParams = Arrays.stream(api.queryParams())
                        .map(qp -> new QueryParamDefinition(
                                qp.name(), qp.type(), qp.required(),
                                qp.defaultValue(), qp.description()))
                        .toList();

                List<HeaderParamDefinition> headerParams = Arrays.stream(api.headers())
                        .map(hp -> new HeaderParamDefinition(
                                hp.name(), hp.required(), hp.description()))
                        .toList();

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
                        headerParams
                );
                apiRegistry.registerApi(definition);
                log.info("[Loom] Scanned builder API: {} {} from {}",
                        api.method(), api.path(), apiClass.getSimpleName());
            }
        }
    }

    private void scanPassthroughApis() {
        Map<String, Object> beans = applicationContext.getBeansWithAnnotation(LoomPassthrough.class);

        for (Map.Entry<String, Object> entry : beans.entrySet()) {
            Class<?> ptClass = entry.getValue().getClass();
            LoomPassthrough pt = ptClass.getAnnotation(LoomPassthrough.class);

            if (pt == null) continue;

            PassthroughDefinition definition = new PassthroughDefinition(
                    pt.method(),
                    pt.path(),
                    pt.upstream(),
                    pt.upstreamPath(),
                    pt.summary(),
                    pt.description(),
                    pt.tags()
            );
            apiRegistry.registerPassthrough(definition);
            log.info("[Loom] Scanned passthrough API: {} {} from {}",
                    pt.method(), pt.path(), ptClass.getSimpleName());
        }
    }
}
