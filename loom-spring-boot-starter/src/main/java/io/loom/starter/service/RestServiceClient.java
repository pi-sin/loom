package io.loom.starter.service;

import io.loom.core.codec.JsonCodec;
import io.loom.core.engine.RetryExecutor;
import io.loom.core.exception.ServiceClientException;
import io.loom.core.service.RetryConfig;
import io.loom.core.service.ServiceClient;
import io.loom.core.service.ServiceConfig;
import io.loom.starter.codec.DslJsonHttpMessageConverter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

@Slf4j
public class RestServiceClient implements ServiceClient {

    private final String name;
    private final RestClient restClient;
    private final RetryExecutor retryExecutor;
    private final RetryConfig retryConfig;

    public RestServiceClient(ServiceConfig config, RetryExecutor retryExecutor, JsonCodec jsonCodec) {
        this.name = config.name();
        this.retryExecutor = retryExecutor;
        this.retryConfig = config.retry();

        var httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(config.connectTimeoutMs()))
                .build();
        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(config.readTimeoutMs()));

        this.restClient = RestClient.builder()
                .baseUrl(config.baseUrl())
                .requestFactory(requestFactory)
                .messageConverters(converters -> {
                    converters.clear();
                    converters.add(new ByteArrayHttpMessageConverter());
                    converters.add(new StringHttpMessageConverter());
                    converters.add(new DslJsonHttpMessageConverter(jsonCodec));
                })
                .build();
        log.info("[Loom] Created service client '{}' -> {}", name, config.baseUrl());
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
                throw new ServiceClientException(name, e.getStatusCode().value(), e.getMessage());
            } catch (Exception e) {
                throw new ServiceClientException(name, e.getMessage(), e);
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
                throw new ServiceClientException(name, e.getStatusCode().value(), e.getMessage());
            } catch (Exception e) {
                throw new ServiceClientException(name, e.getMessage(), e);
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
                throw new ServiceClientException(name, e.getStatusCode().value(), e.getMessage());
            } catch (Exception e) {
                throw new ServiceClientException(name, e.getMessage(), e);
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
                throw new ServiceClientException(name, e.getStatusCode().value(), e.getMessage());
            } catch (Exception e) {
                throw new ServiceClientException(name, e.getMessage(), e);
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
                throw new ServiceClientException(name, e.getStatusCode().value(), e.getMessage());
            } catch (Exception e) {
                throw new ServiceClientException(name, e.getMessage(), e);
            }
        }, retryConfig, name + " PATCH " + path);
    }
}
