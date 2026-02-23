package io.loom.starter.service;

import io.loom.core.engine.RetryExecutor;
import io.loom.core.service.RetryConfig;
import io.loom.core.service.ServiceConfig;
import io.loom.starter.config.LoomProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@Slf4j
@AutoConfiguration
@ConditionalOnProperty(prefix = "loom", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LoomProxyAutoConfiguration {

    @Bean
    public ServiceClientRegistry serviceClientRegistry(LoomProperties properties,
                                                        RetryExecutor retryExecutor) {
        ServiceClientRegistry registry = new ServiceClientRegistry();

        properties.getServices().forEach((name, props) -> {
            RetryConfig retryConfig = new RetryConfig(
                    props.getRetry().getMaxAttempts(),
                    props.getRetry().getInitialDelayMs(),
                    props.getRetry().getMultiplier(),
                    props.getRetry().getMaxDelayMs()
            );

            ServiceConfig config = new ServiceConfig(
                    name,
                    props.getBaseUrl(),
                    props.getConnectTimeoutMs(),
                    props.getReadTimeoutMs(),
                    retryConfig
            );

            RestServiceClient client = new RestServiceClient(config, retryExecutor);
            registry.register(name, client);
        });

        log.info("[Loom] Configured {} service clients", properties.getServices().size());
        return registry;
    }
}
