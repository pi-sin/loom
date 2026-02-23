package io.loom.starter.upstream;

import io.loom.core.engine.RetryExecutor;
import io.loom.core.exception.UpstreamException;
import io.loom.core.upstream.RetryConfig;
import io.loom.core.upstream.UpstreamClient;
import io.loom.core.upstream.UpstreamConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.Map;

@Slf4j
public class RestClientUpstreamClient implements UpstreamClient {

    private final String name;
    private final RestClient restClient;
    private final RetryExecutor retryExecutor;
    private final RetryConfig retryConfig;

    public RestClientUpstreamClient(UpstreamConfig config, RetryExecutor retryExecutor) {
        this.name = config.name();
        this.retryExecutor = retryExecutor;
        this.retryConfig = config.retry();
        this.restClient = RestClient.builder()
                .baseUrl(config.baseUrl())
                .build();
        log.info("[Loom] Created upstream client '{}' -> {}", name, config.baseUrl());
    }

    @Override
    public <T> T get(String path, Class<T> responseType) {
        return get(path, responseType, Map.of());
    }

    @Override
    public <T> T get(String path, Class<T> responseType, Map<String, String> headers) {
        return retryExecutor.execute(() -> {
            try {
                var spec = restClient.get().uri(path);
                headers.forEach(spec::header);
                return spec.retrieve().body(responseType);
            } catch (RestClientResponseException e) {
                throw new UpstreamException(name, e.getStatusCode().value(), e.getMessage());
            } catch (Exception e) {
                throw new UpstreamException(name, e.getMessage(), e);
            }
        }, retryConfig, name + " GET " + path);
    }

    @Override
    public <T> T post(String path, Object body, Class<T> responseType) {
        return post(path, body, responseType, Map.of());
    }

    @Override
    public <T> T post(String path, Object body, Class<T> responseType, Map<String, String> headers) {
        return retryExecutor.execute(() -> {
            try {
                var spec = restClient.post().uri(path);
                headers.forEach(spec::header);
                if (body != null) {
                    spec.body(body);
                }
                return spec.retrieve().body(responseType);
            } catch (RestClientResponseException e) {
                throw new UpstreamException(name, e.getStatusCode().value(), e.getMessage());
            } catch (Exception e) {
                throw new UpstreamException(name, e.getMessage(), e);
            }
        }, retryConfig, name + " POST " + path);
    }

    @Override
    public <T> T put(String path, Object body, Class<T> responseType) {
        return put(path, body, responseType, Map.of());
    }

    @Override
    public <T> T put(String path, Object body, Class<T> responseType, Map<String, String> headers) {
        return retryExecutor.execute(() -> {
            try {
                var spec = restClient.put().uri(path);
                headers.forEach(spec::header);
                if (body != null) {
                    spec.body(body);
                }
                return spec.retrieve().body(responseType);
            } catch (RestClientResponseException e) {
                throw new UpstreamException(name, e.getStatusCode().value(), e.getMessage());
            } catch (Exception e) {
                throw new UpstreamException(name, e.getMessage(), e);
            }
        }, retryConfig, name + " PUT " + path);
    }

    @Override
    public <T> T delete(String path, Class<T> responseType) {
        return delete(path, responseType, Map.of());
    }

    @Override
    public <T> T delete(String path, Class<T> responseType, Map<String, String> headers) {
        return retryExecutor.execute(() -> {
            try {
                var spec = restClient.delete().uri(path);
                headers.forEach(spec::header);
                return spec.retrieve().body(responseType);
            } catch (RestClientResponseException e) {
                throw new UpstreamException(name, e.getStatusCode().value(), e.getMessage());
            } catch (Exception e) {
                throw new UpstreamException(name, e.getMessage(), e);
            }
        }, retryConfig, name + " DELETE " + path);
    }

    @Override
    public <T> T patch(String path, Object body, Class<T> responseType) {
        return patch(path, body, responseType, Map.of());
    }

    @Override
    public <T> T patch(String path, Object body, Class<T> responseType, Map<String, String> headers) {
        return retryExecutor.execute(() -> {
            try {
                var spec = restClient.patch().uri(path);
                headers.forEach(spec::header);
                if (body != null) {
                    spec.body(body);
                }
                return spec.retrieve().body(responseType);
            } catch (RestClientResponseException e) {
                throw new UpstreamException(name, e.getStatusCode().value(), e.getMessage());
            } catch (Exception e) {
                throw new UpstreamException(name, e.getMessage(), e);
            }
        }, retryConfig, name + " PATCH " + path);
    }
}
