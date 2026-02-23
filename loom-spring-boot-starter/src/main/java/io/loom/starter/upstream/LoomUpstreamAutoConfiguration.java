package io.loom.starter.upstream;

import io.loom.core.engine.RetryExecutor;
import io.loom.core.upstream.RetryConfig;
import io.loom.core.upstream.UpstreamConfig;
import io.loom.starter.config.LoomProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@Slf4j
@AutoConfiguration
@ConditionalOnProperty(prefix = "loom", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LoomUpstreamAutoConfiguration {

    @Bean
    public UpstreamClientRegistry upstreamClientRegistry(LoomProperties properties,
                                                          RetryExecutor retryExecutor) {
        UpstreamClientRegistry registry = new UpstreamClientRegistry();

        properties.getUpstreams().forEach((name, props) -> {
            RetryConfig retryConfig = new RetryConfig(
                    props.getRetry().getMaxAttempts(),
                    props.getRetry().getInitialDelayMs(),
                    props.getRetry().getMultiplier(),
                    props.getRetry().getMaxDelayMs()
            );

            UpstreamConfig config = new UpstreamConfig(
                    name,
                    props.getBaseUrl(),
                    props.getConnectTimeoutMs(),
                    props.getReadTimeoutMs(),
                    retryConfig
            );

            RestClientUpstreamClient client = new RestClientUpstreamClient(config, retryExecutor);
            registry.register(name, client);
        });

        log.info("[Loom] Configured {} upstream clients", properties.getUpstreams().size());
        return registry;
    }
}
