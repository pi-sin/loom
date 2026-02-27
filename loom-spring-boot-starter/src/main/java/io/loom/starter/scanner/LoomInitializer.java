package io.loom.starter.scanner;

import io.loom.core.engine.DagCompiler;
import io.loom.core.engine.DagExecutor;
import io.loom.core.registry.ApiRegistry;
import io.loom.starter.config.LoomProperties;
import io.loom.starter.service.ServiceClientRegistry;
import io.loom.starter.web.LoomHandlerAdapter;
import io.loom.starter.web.LoomHandlerMapping;
import io.loom.starter.registry.InterceptorRegistry;
import io.loom.core.codec.JsonCodec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.web.servlet.HandlerAdapter;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class LoomInitializer implements SmartInitializingSingleton {

    private final ApplicationContext applicationContext;
    private final DagCompiler dagCompiler;
    private final ApiRegistry apiRegistry;
    private final ServiceClientRegistry serviceClientRegistry;

    public LoomInitializer(ApplicationContext applicationContext,
                           DagCompiler dagCompiler,
                           ApiRegistry apiRegistry,
                           ServiceClientRegistry serviceClientRegistry) {
        this.applicationContext = applicationContext;
        this.dagCompiler = dagCompiler;
        this.apiRegistry = apiRegistry;
        this.serviceClientRegistry = serviceClientRegistry;
    }

    @Override
    public void afterSingletonsInstantiated() {
        log.info("[Loom] Initializing Loom framework...");

        // Scan annotations
        LoomAnnotationScanner scanner = new LoomAnnotationScanner(
                applicationContext, dagCompiler, apiRegistry, serviceClientRegistry);
        scanner.scan();

        int apiCount = apiRegistry.getAllApis().size();
        log.info("[Loom] Registered {} APIs", apiCount);

        log.info("[Loom] Framework initialized successfully");
    }

    @Bean
    public HandlerMapping loomHandlerMapping(ApiRegistry apiRegistry) {
        return new LoomHandlerMapping(apiRegistry);
    }

    @Bean
    public HandlerAdapter loomHandlerAdapter(DagExecutor dagExecutor,
                                              InterceptorRegistry interceptorRegistry,
                                              ServiceClientRegistry serviceClientRegistry,
                                              JsonCodec jsonCodec,
                                              LoomProperties loomProperties) {
        return new LoomHandlerAdapter(dagExecutor, interceptorRegistry,
                serviceClientRegistry, jsonCodec, loomProperties.getMaxRequestBodySize());
    }
}
