package io.loom.starter.swagger;

import io.loom.core.model.ApiDefinition;
import io.loom.core.model.HeaderParamDefinition;
import io.loom.core.model.PassthroughDefinition;
import io.loom.core.model.QueryParamDefinition;
import io.loom.core.registry.ApiRegistry;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.NumberSchema;
import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.parameters.PathParameter;
import io.swagger.v3.oas.models.parameters.QueryParameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@AutoConfiguration
@ConditionalOnClass(OpenApiCustomizer.class)
@ConditionalOnProperty(prefix = "loom.swagger", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LoomOpenApiConfiguration {

    private static final Pattern PATH_VARIABLE_PATTERN = Pattern.compile("\\{([^}]+)}");

    @Bean
    public OpenApiCustomizer loomOpenApiCustomizer(ApiRegistry apiRegistry) {
        return openApi -> {
            // Remove internal Loom UI endpoints auto-discovered by springdoc
            if (openApi.getPaths() != null) {
                openApi.getPaths().keySet().removeIf(path -> path.startsWith("/loom/"));
            }

            for (ApiDefinition api : apiRegistry.getAllApis()) {
                addApiPath(openApi, api);
            }
            for (PassthroughDefinition pt : apiRegistry.getAllPassthroughs()) {
                addPassthroughPath(openApi, pt);
            }
            log.info("[Loom] Registered {} API routes and {} passthrough routes in OpenAPI spec",
                    apiRegistry.getAllApis().size(), apiRegistry.getAllPassthroughs().size());
        };
    }

    private void addApiPath(OpenAPI openApi, ApiDefinition api) {
        Operation operation = new Operation();

        if (!api.summary().isEmpty()) {
            operation.setSummary(api.summary());
        }
        if (!api.description().isEmpty()) {
            operation.setDescription(api.description());
        }
        if (api.tags() != null && api.tags().length > 0) {
            operation.setTags(Arrays.asList(api.tags()));
        }

        // Path parameters from {varName} in path
        extractPathParams(api.path()).forEach(operation::addParametersItem);

        // Query parameters
        if (api.queryParams() != null) {
            for (QueryParamDefinition qp : api.queryParams()) {
                QueryParameter param = new QueryParameter();
                param.setName(qp.name());
                param.setRequired(qp.required());
                param.setSchema(mapTypeToSchema(qp.type()));
                if (!qp.description().isEmpty()) {
                    param.setDescription(qp.description());
                }
                if (!qp.defaultValue().isEmpty()) {
                    param.getSchema().setDefault(qp.defaultValue());
                }
                operation.addParametersItem(param);
            }
        }

        // Header parameters
        if (api.headerParams() != null) {
            for (HeaderParamDefinition hp : api.headerParams()) {
                HeaderParameter param = new HeaderParameter();
                param.setName(hp.name());
                param.setRequired(hp.required());
                param.setSchema(new StringSchema());
                if (!hp.description().isEmpty()) {
                    param.setDescription(hp.description());
                }
                operation.addParametersItem(param);
            }
        }

        // Request body
        if (api.requestType() != null && api.requestType() != void.class) {
            RequestBody requestBody = new RequestBody();
            requestBody.setRequired(true);
            Content content = new Content();
            MediaType mediaType = new MediaType();
            Schema<?> schema = new Schema<>();
            schema.set$ref("#/components/schemas/" + api.requestType().getSimpleName());
            mediaType.setSchema(schema);
            content.addMediaType("application/json", mediaType);
            requestBody.setContent(content);
            operation.setRequestBody(requestBody);
        }

        // Response
        ApiResponses responses = new ApiResponses();
        ApiResponse okResponse = new ApiResponse();
        okResponse.setDescription("Successful response");
        if (api.responseType() != null && api.responseType() != void.class) {
            Content content = new Content();
            MediaType mediaType = new MediaType();
            Schema<?> schema = new Schema<>();
            schema.set$ref("#/components/schemas/" + api.responseType().getSimpleName());
            mediaType.setSchema(schema);
            content.addMediaType("application/json", mediaType);
            okResponse.setContent(content);
        }
        responses.addApiResponse("200", okResponse);
        operation.setResponses(responses);

        String path = springToOpenApiPath(api.path());
        PathItem pathItem = openApi.getPaths() != null ? openApi.getPaths().get(path) : null;
        if (pathItem == null) {
            pathItem = new PathItem();
        }
        setOperationOnPathItem(pathItem, api.method(), operation);

        if (openApi.getPaths() == null) {
            openApi.setPaths(new io.swagger.v3.oas.models.Paths());
        }
        openApi.getPaths().addPathItem(path, pathItem);
    }

    private void addPassthroughPath(OpenAPI openApi, PassthroughDefinition pt) {
        Operation operation = new Operation();

        if (!pt.summary().isEmpty()) {
            operation.setSummary(pt.summary());
        } else {
            operation.setSummary("Passthrough to " + pt.upstream() + pt.upstreamPath());
        }
        if (!pt.description().isEmpty()) {
            operation.setDescription(pt.description());
        }
        if (pt.tags() != null && pt.tags().length > 0) {
            operation.setTags(Arrays.asList(pt.tags()));
        } else {
            operation.addTagsItem("Passthrough");
        }

        // Path parameters
        extractPathParams(pt.path()).forEach(operation::addParametersItem);

        ApiResponses responses = new ApiResponses();
        ApiResponse okResponse = new ApiResponse();
        okResponse.setDescription("Proxied response from " + pt.upstream());
        responses.addApiResponse("200", okResponse);
        operation.setResponses(responses);

        String path = springToOpenApiPath(pt.path());
        PathItem pathItem = openApi.getPaths() != null ? openApi.getPaths().get(path) : null;
        if (pathItem == null) {
            pathItem = new PathItem();
        }
        setOperationOnPathItem(pathItem, pt.method(), operation);

        if (openApi.getPaths() == null) {
            openApi.setPaths(new io.swagger.v3.oas.models.Paths());
        }
        openApi.getPaths().addPathItem(path, pathItem);
    }

    private List<PathParameter> extractPathParams(String path) {
        Matcher matcher = PATH_VARIABLE_PATTERN.matcher(path);
        return matcher.results()
                .map(mr -> {
                    PathParameter param = new PathParameter();
                    param.setName(mr.group(1));
                    param.setRequired(true);
                    param.setSchema(new StringSchema());
                    return param;
                })
                .toList();
    }

    private String springToOpenApiPath(String path) {
        // Spring path templates already use {varName} which is OpenAPI-compatible
        return path;
    }

    @SuppressWarnings("rawtypes")
    private Schema mapTypeToSchema(Class<?> type) {
        if (type == Integer.class || type == int.class || type == Long.class || type == long.class) {
            return new IntegerSchema();
        } else if (type == Double.class || type == double.class || type == Float.class || type == float.class) {
            return new NumberSchema();
        } else if (type == Boolean.class || type == boolean.class) {
            return new BooleanSchema();
        }
        return new StringSchema();
    }

    private void setOperationOnPathItem(PathItem pathItem, String method, Operation operation) {
        switch (method.toUpperCase()) {
            case "GET" -> pathItem.setGet(operation);
            case "POST" -> pathItem.setPost(operation);
            case "PUT" -> pathItem.setPut(operation);
            case "DELETE" -> pathItem.setDelete(operation);
            case "PATCH" -> pathItem.setPatch(operation);
            case "HEAD" -> pathItem.setHead(operation);
            case "OPTIONS" -> pathItem.setOptions(operation);
            default -> pathItem.setGet(operation);
        }
    }
}
