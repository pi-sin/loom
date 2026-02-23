package io.loom.starter.scanner;

import io.loom.core.engine.DagCompiler;
import io.loom.core.engine.DagExecutor;
import io.loom.core.registry.ApiRegistry;
import io.loom.starter.config.LoomProperties;
import io.loom.starter.upstream.UpstreamClientRegistry;
import io.loom.starter.web.LoomHandlerAdapter;
import io.loom.starter.web.LoomHandlerMapping;
import io.loom.starter.registry.GuardRegistry;
import io.loom.starter.registry.MiddlewareRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final LoomProperties properties;

    public LoomInitializer(ApplicationContext applicationContext,
                           DagCompiler dagCompiler,
                           ApiRegistry apiRegistry,
                           LoomProperties properties) {
        this.applicationContext = applicationContext;
        this.dagCompiler = dagCompiler;
        this.apiRegistry = apiRegistry;
        this.properties = properties;
    }

    @Override
    public void afterSingletonsInstantiated() {
        log.info("[Loom] Initializing Loom framework...");

        // 1. Scan annotations
        LoomAnnotationScanner scanner = new LoomAnnotationScanner(
                applicationContext, dagCompiler, apiRegistry);
        scanner.scan();

        // 2. Parse YAML config
        LoomYamlParser yamlParser = new LoomYamlParser(apiRegistry);
        yamlParser.parse(properties.getConfigFile());

        int apiCount = apiRegistry.getAllApis().size();
        int ptCount = apiRegistry.getAllPassthroughs().size();
        log.info("[Loom] Registered {} builder APIs, {} passthrough routes",
                apiCount, ptCount);

        log.info("[Loom] Framework initialized successfully");
    }

    @Bean
    public HandlerMapping loomHandlerMapping(ApiRegistry apiRegistry) {
        return new LoomHandlerMapping(apiRegistry);
    }

    @Bean
    public HandlerAdapter loomHandlerAdapter(DagExecutor dagExecutor,
                                              MiddlewareRegistry middlewareRegistry,
                                              GuardRegistry guardRegistry,
                                              UpstreamClientRegistry upstreamClientRegistry,
                                              ObjectMapper objectMapper) {
        return new LoomHandlerAdapter(dagExecutor, middlewareRegistry,
                guardRegistry, upstreamClientRegistry, objectMapper);
    }
}
