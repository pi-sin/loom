package io.loom.starter.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.HashMap;
import java.util.Map;

@Data
@ConfigurationProperties(prefix = "loom")
public class LoomProperties {

    private boolean enabled = true;
    private String configFile = "loom.yml";
    private Map<String, UpstreamProperties> upstreams = new HashMap<>();
    private UiProperties ui = new UiProperties();
    private SwaggerProperties swagger = new SwaggerProperties();

    @Data
    public static class UpstreamProperties {
        private String baseUrl;
        private long connectTimeoutMs = 5000;
        private long readTimeoutMs = 30000;
        private RetryProperties retry = new RetryProperties();
    }

    @Data
    public static class RetryProperties {
        private int maxAttempts = 3;
        private long initialDelayMs = 100;
        private double multiplier = 2.0;
        private long maxDelayMs = 5000;
    }

    @Data
    public static class UiProperties {
        private boolean enabled = true;
    }

    @Data
    public static class SwaggerProperties {
        private boolean enabled = true;
    }
}
