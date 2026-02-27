package io.loom.starter.service;

import io.loom.core.codec.JsonCodec;
import io.loom.core.engine.RetryExecutor;
import io.loom.core.exception.LoomException;
import io.loom.core.model.ProxyPathTemplate;
import io.loom.core.service.RetryConfig;
import io.loom.core.service.RouteConfig;
import io.loom.core.service.ServiceConfig;
import io.loom.starter.config.LoomProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@AutoConfiguration
@ConditionalOnProperty(prefix = "loom", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LoomProxyAutoConfiguration {

    @Bean
    public ServiceClientRegistry serviceClientRegistry(LoomProperties properties,
                                                        RetryExecutor retryExecutor,
                                                        JsonCodec jsonCodec) {
        ServiceClientRegistry registry = new ServiceClientRegistry();

        properties.getServices().forEach((name, props) -> {
            RetryConfig serviceRetry = toRetryConfig(props.getRetry());

            // Build RouteConfig objects for each route
            Map<String, RouteConfig> routeConfigs = new HashMap<>();
            props.getRoutes().forEach((routeName, routeProps) -> {
                if (routeProps.getPath() == null || routeProps.getPath().isBlank()) {
                    throw new LoomException("Route '" + routeName + "' of service '"
                            + name + "' is missing required 'path' property");
                }

                RetryConfig routeRetry = routeProps.getRetry() != null
                        ? toRetryConfig(routeProps.getRetry()) : null;

                RouteConfig routeConfig = new RouteConfig(
                        routeName,
                        routeProps.getPath(),
                        routeProps.getMethod(),
                        routeProps.getConnectTimeoutMs(),
                        routeProps.getReadTimeoutMs(),
                        routeRetry,
                        ProxyPathTemplate.compile(routeProps.getPath())
                );
                routeConfigs.put(routeName, routeConfig);
            });

            ServiceConfig serviceConfig = new ServiceConfig(
                    name,
                    props.getUrl(),
                    props.getConnectTimeoutMs(),
                    props.getReadTimeoutMs(),
                    serviceRetry,
                    routeConfigs
            );

            // Create service-level client
            RestServiceClient serviceClient = new RestServiceClient(
                    name, props.getUrl(),
                    props.getConnectTimeoutMs(), props.getReadTimeoutMs(),
                    serviceRetry, retryExecutor, jsonCodec
            );
            registry.register(name, serviceClient);
            registry.registerServiceConfig(name, serviceConfig);

            // Register flat route configs for single-lookup access
            routeConfigs.forEach((routeName, routeConfig) ->
                    registry.registerRouteConfig(name, routeName, routeConfig));

            // Create route-level clients only when timeouts differ from service defaults
            routeConfigs.forEach((routeName, routeConfig) -> {
                if (serviceConfig.routeNeedsCustomClient(routeConfig)) {
                    RestServiceClient routeClient = new RestServiceClient(
                            name + "." + routeName, props.getUrl(),
                            serviceConfig.effectiveConnectTimeout(routeConfig),
                            serviceConfig.effectiveReadTimeout(routeConfig),
                            serviceConfig.effectiveRetry(routeConfig),
                            retryExecutor, jsonCodec
                    );
                    registry.registerRouteClient(name, routeName, routeClient);
                }
            });

            int routeCount = routeConfigs.size();
            if (routeCount > 0) {
                log.info("[Loom] Service '{}' configured with {} route(s)", name, routeCount);
            }
        });

        log.info("[Loom] Configured {} service clients", properties.getServices().size());
        return registry;
    }

    private RetryConfig toRetryConfig(LoomProperties.RetryProperties props) {
        return new RetryConfig(
                props.getMaxAttempts(),
                props.getInitialDelayMs(),
                props.getMultiplier(),
                props.getMaxDelayMs()
        );
    }
}
