package io.loom.starter.config;

import io.loom.core.codec.DslJsonCodec;
import io.loom.core.codec.JsonCodec;
import io.loom.core.engine.DagCompiler;
import io.loom.core.engine.DagExecutor;
import io.loom.core.engine.DagValidator;
import io.loom.core.engine.RetryExecutor;
import io.loom.core.registry.ApiRegistry;
import io.loom.core.registry.BuilderFactory;
import io.loom.starter.context.SpringBuilderFactory;
import io.loom.starter.registry.InMemoryApiRegistry;
import io.loom.starter.registry.InterceptorRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

import java.util.List;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@AutoConfiguration
@ConditionalOnProperty(prefix = "loom", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(LoomProperties.class)
public class LoomAutoConfiguration {

    @Bean
    public JsonCodec jsonCodec() {
        return new DslJsonCodec();
    }

    @Bean
    public ExecutorService loomVirtualThreadExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean
    public DagValidator dagValidator() {
        return new DagValidator();
    }

    @Bean
    public DagCompiler dagCompiler() {
        return new DagCompiler();
    }

    @Bean
    public RetryExecutor retryExecutor() {
        return new RetryExecutor();
    }

    @Bean
    public BuilderFactory builderFactory(ApplicationContext applicationContext) {
        return new SpringBuilderFactory(applicationContext);
    }

    @Bean
    public DagExecutor dagExecutor(BuilderFactory builderFactory) {
        return new DagExecutor(builderFactory);
    }

    @Bean
    public ApiRegistry apiRegistry() {
        return new InMemoryApiRegistry();
    }

    @Bean
    public InterceptorRegistry interceptorRegistry(ApplicationContext applicationContext) {
        return new InterceptorRegistry(applicationContext);
    }

    @Bean
    public static LoomBeanRegistry loomBeanRegistry(ApplicationContext applicationContext, LoomProperties properties) {
        List<String> configured = properties.getBasePackages();
        String[] packages;
        if (configured != null && !configured.isEmpty()) {
            packages = configured.toArray(String[]::new);
        } else {
            packages = AutoConfigurationPackages.get(applicationContext).toArray(String[]::new);
        }
        return new LoomBeanRegistry(packages);
    }
}
