package io.loom.starter.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

@Data
@ConfigurationProperties(prefix = "loom")
public class LoomProperties {

    private boolean enabled = true;

    private Map<String, ServiceProperties> services = new HashMap<>();

    private UiProperties ui = new UiProperties();

    private SwaggerProperties swagger = new SwaggerProperties();

    private long maxRequestBodySize = 10485760; // 10MB

    private List<String> basePackages = new ArrayList<>();

    @Data
    public static class ServiceProperties {
        private String url;

        private long connectTimeoutMs = 5000;

        private long readTimeoutMs = 30000;

        private RetryProperties retry = new RetryProperties();

        private Map<String, RouteProperties> routes = new HashMap<>();
    }

    @Data
    public static class RouteProperties {
        private String path;

        private String method = "GET";

        private long connectTimeoutMs = -1;

        private long readTimeoutMs = -1;

        private RetryProperties retry;
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
