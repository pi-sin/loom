package io.loom.starter.service;

import io.loom.core.codec.JsonCodec;
import io.loom.core.engine.RetryExecutor;
import io.loom.core.exception.LoomServiceClientException;
import io.loom.core.service.RetryConfig;
import io.loom.core.service.ServiceClient;
import io.loom.core.service.ServiceResponse;
import io.loom.starter.codec.DslJsonHttpMessageConverter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

@Slf4j
public class RestServiceClient implements ServiceClient {

    private final String name;
    private final RestClient restClient;
    private final RetryExecutor retryExecutor;
    private final RetryConfig retryConfig;
    private final JsonCodec jsonCodec;

    public RestServiceClient(String name, String url, long connectTimeoutMs,
                              long readTimeoutMs, RetryConfig retryConfig,
                              RetryExecutor retryExecutor, JsonCodec jsonCodec) {
        this.name = name;
        this.retryExecutor = retryExecutor;
        this.retryConfig = retryConfig;
        this.jsonCodec = jsonCodec;

        var httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .build();
        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

        this.restClient = RestClient.builder()
                .baseUrl(url)
                .requestFactory(requestFactory)
                .messageConverters(converters -> {
                    converters.clear();
                    converters.add(new ByteArrayHttpMessageConverter());
                    converters.add(new StringHttpMessageConverter());
                    converters.add(new DslJsonHttpMessageConverter(jsonCodec));
                })
                .build();
        log.info("[Loom] Created service client '{}' -> {}", name, url);
    }

    @Override
    public <T> T get(String path, Class<T> responseType) {
        return get(path, responseType, Map.of());
    }

    @Override
    public <T> T get(String path, Class<T> responseType, Map<String, String> headers) {
        String opName = name + " GET " + path;
        return retryExecutor.execute(() -> {
            try {
                var spec = restClient.get().uri(path);
                headers.forEach(spec::header);
                return spec.retrieve().body(responseType);
            } catch (RestClientResponseException e) {
                throw new LoomServiceClientException(name, e.getStatusCode().value(), e.getMessage(), e);
            } catch (Exception e) {
                throw new LoomServiceClientException(name, e.getMessage(), e);
            }
        }, retryConfig, opName);
    }

    @Override
    public <T> T post(String path, Object body, Class<T> responseType) {
        return post(path, body, responseType, Map.of());
    }

    @Override
    public <T> T post(String path, Object body, Class<T> responseType, Map<String, String> headers) {
        String opName = name + " POST " + path;
        return retryExecutor.execute(() -> {
            try {
                var spec = restClient.post().uri(path);
                headers.forEach(spec::header);
                if (body != null) {
                    spec.body(body);
                }
                return spec.retrieve().body(responseType);
            } catch (RestClientResponseException e) {
                throw new LoomServiceClientException(name, e.getStatusCode().value(), e.getMessage(), e);
            } catch (Exception e) {
                throw new LoomServiceClientException(name, e.getMessage(), e);
            }
        }, retryConfig, opName);
    }

    @Override
    public <T> T put(String path, Object body, Class<T> responseType) {
        return put(path, body, responseType, Map.of());
    }

    @Override
    public <T> T put(String path, Object body, Class<T> responseType, Map<String, String> headers) {
        String opName = name + " PUT " + path;
        return retryExecutor.execute(() -> {
            try {
                var spec = restClient.put().uri(path);
                headers.forEach(spec::header);
                if (body != null) {
                    spec.body(body);
                }
                return spec.retrieve().body(responseType);
            } catch (RestClientResponseException e) {
                throw new LoomServiceClientException(name, e.getStatusCode().value(), e.getMessage(), e);
            } catch (Exception e) {
                throw new LoomServiceClientException(name, e.getMessage(), e);
            }
        }, retryConfig, opName);
    }

    @Override
    public <T> T delete(String path, Class<T> responseType) {
        return delete(path, responseType, Map.of());
    }

    @Override
    public <T> T delete(String path, Class<T> responseType, Map<String, String> headers) {
        String opName = name + " DELETE " + path;
        return retryExecutor.execute(() -> {
            try {
                var spec = restClient.delete().uri(path);
                headers.forEach(spec::header);
                return spec.retrieve().body(responseType);
            } catch (RestClientResponseException e) {
                throw new LoomServiceClientException(name, e.getStatusCode().value(), e.getMessage(), e);
            } catch (Exception e) {
                throw new LoomServiceClientException(name, e.getMessage(), e);
            }
        }, retryConfig, opName);
    }

    @Override
    public <T> T patch(String path, Object body, Class<T> responseType) {
        return patch(path, body, responseType, Map.of());
    }

    @Override
    public <T> T patch(String path, Object body, Class<T> responseType, Map<String, String> headers) {
        String opName = name + " PATCH " + path;
        return retryExecutor.execute(() -> {
            try {
                var spec = restClient.patch().uri(path);
                headers.forEach(spec::header);
                if (body != null) {
                    spec.body(body);
                }
                return spec.retrieve().body(responseType);
            } catch (RestClientResponseException e) {
                throw new LoomServiceClientException(name, e.getStatusCode().value(), e.getMessage(), e);
            } catch (Exception e) {
                throw new LoomServiceClientException(name, e.getMessage(), e);
            }
        }, retryConfig, opName);
    }

    @Override
    public ServiceResponse<byte[]> proxy(String method, String path, byte[] body, Map<String, String> headers) {
        HttpMethod httpMethod = HttpMethod.valueOf(method.toUpperCase());
        String opName = name + " " + httpMethod.name() + " " + path;
        return retryExecutor.execute(() -> {
            try {
                var spec = restClient.method(httpMethod).uri(path);
                if (headers != null) {
                    headers.forEach(spec::header);
                }
                if (body != null && body.length > 0) {
                    spec.body(body);
                }
                ResponseEntity<byte[]> entity = spec.retrieve().toEntity(byte[].class);
                return buildByteResponse(entity);
            } catch (RestClientResponseException e) {
                return new ServiceResponse<>(
                        null,
                        e.getStatusCode().value(),
                        toMultiValueMap(e.getResponseHeaders()),
                        e.getResponseBodyAsByteArray(),
                        extractContentType(e.getResponseHeaders())
                );
            } catch (Exception e) {
                throw new LoomServiceClientException(name, e.getMessage(), e);
            }
        }, retryConfig, opName);
    }

    @Override
    public <T> ServiceResponse<T> exchange(String method, String path, Object body,
                                            Class<T> responseType, Map<String, String> headers) {
        HttpMethod httpMethod = HttpMethod.valueOf(method.toUpperCase());
        String opName = name + " " + httpMethod.name() + " " + path;
        return retryExecutor.execute(() -> {
            try {
                var spec = restClient.method(httpMethod).uri(path);
                if (headers != null) {
                    headers.forEach(spec::header);
                }
                if (body != null) {
                    spec.body(body);
                }
                ResponseEntity<byte[]> entity = spec.retrieve().toEntity(byte[].class);
                byte[] rawBody = entity.getBody() != null ? entity.getBody() : new byte[0];
                T data = deserializeIfPresent(rawBody, responseType);
                return new ServiceResponse<>(
                        data,
                        entity.getStatusCode().value(),
                        toMultiValueMap(entity.getHeaders()),
                        rawBody,
                        extractContentType(entity.getHeaders())
                );
            } catch (RestClientResponseException e) {
                return new ServiceResponse<>(
                        null,
                        e.getStatusCode().value(),
                        toMultiValueMap(e.getResponseHeaders()),
                        e.getResponseBodyAsByteArray(),
                        extractContentType(e.getResponseHeaders())
                );
            } catch (Exception e) {
                throw new LoomServiceClientException(name, e.getMessage(), e);
            }
        }, retryConfig, opName);
    }

    private ServiceResponse<byte[]> buildByteResponse(ResponseEntity<byte[]> entity) {
        byte[] rawBody = entity.getBody() != null ? entity.getBody() : new byte[0];
        return new ServiceResponse<>(
                rawBody,
                entity.getStatusCode().value(),
                toMultiValueMap(entity.getHeaders()),
                rawBody,
                extractContentType(entity.getHeaders())
        );
    }

    private <T> T deserializeIfPresent(byte[] rawBody, Class<T> responseType) {
        if (rawBody == null || rawBody.length == 0) {
            return null;
        }
        if (responseType == byte[].class) {
            @SuppressWarnings("unchecked")
            T result = (T) rawBody;
            return result;
        }
        try {
            return jsonCodec.readValue(rawBody, responseType);
        } catch (IOException e) {
            log.warn("[Loom] Failed to deserialize response body for service '{}' as {}: {}",
                    name, responseType.getSimpleName(), e.getMessage());
            return null;
        }
    }

    private static Map<String, List<String>> toMultiValueMap(org.springframework.http.HttpHeaders httpHeaders) {
        if (httpHeaders == null) {
            return Map.of();
        }
        Map<String, List<String>> result = new LinkedHashMap<>();
        httpHeaders.forEach((key, values) -> result.put(key, List.of(values.toArray(String[]::new))));
        return Collections.unmodifiableMap(result);
    }

    private static String extractContentType(org.springframework.http.HttpHeaders httpHeaders) {
        if (httpHeaders == null || httpHeaders.getContentType() == null) {
            return "application/octet-stream";
        }
        return httpHeaders.getContentType().toString();
    }
}
