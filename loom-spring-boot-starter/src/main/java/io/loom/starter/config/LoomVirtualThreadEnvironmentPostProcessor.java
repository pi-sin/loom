package io.loom.starter.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

/**
 * Sets spring.threads.virtual.enabled=true as a low-priority default so that
 * Loom's virtual-thread execution extends to the embedded web server.
 * Users can still override this in their own application.yml / properties.
 */
public class LoomVirtualThreadEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!environment.containsProperty("spring.threads.virtual.enabled")) {
            environment.getPropertySources().addLast(
                    new MapPropertySource("loomVirtualThreadDefaults",
                            Map.of("spring.threads.virtual.enabled", "true")));
        }
    }
}
