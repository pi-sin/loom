package io.loom.starter.swagger;

import io.loom.core.model.ApiDefinition;
import io.loom.core.model.HeaderParamDefinition;
import io.loom.core.model.PassthroughDefinition;
import io.loom.core.model.QueryParamDefinition;
import io.loom.core.registry.ApiRegistry;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.converter.ResolvedSchema;
import io.swagger.v3.oas.models.Components;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@AutoConfiguration
@ConditionalOnClass(OpenApiCustomizer.class)
@ConditionalOnProperty(prefix = "loom.swagger", name = "enabled", havingValue = "true", matchIfMissing = false)
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

            // Resolve domain type schemas for request/response types
            for (ApiDefinition api : apiRegistry.getAllApis()) {
                if (api.requestType() != null && api.requestType() != void.class) {
                    resolveAndAddSchema(openApi, api.requestType());
                }
                if (api.responseType() != null && api.responseType() != void.class) {
                    resolveAndAddSchema(openApi, api.responseType());
                }
            }

            // Remove unreferenced schemas (Spring internals from controller scanning)
            cleanupUnreferencedSchemas(openApi);

            // Sort schemas alphabetically
            sortSchemas(openApi);

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

    // ── Schema resolution & cleanup ──────────────────────────────────────

    @SuppressWarnings("rawtypes")
    private void resolveAndAddSchema(OpenAPI openApi, Class<?> type) {
        ResolvedSchema resolved = ModelConverters.getInstance()
                .resolveAsResolvedSchema(new AnnotatedType(type));
        if (resolved == null || resolved.schema == null) {
            return;
        }
        if (openApi.getComponents() == null) {
            openApi.setComponents(new Components());
        }
        Map<String, Schema> schemas = openApi.getComponents().getSchemas();
        if (schemas == null) {
            schemas = new LinkedHashMap<>();
            openApi.getComponents().setSchemas(schemas);
        }
        // Add the top-level schema
        schemas.put(type.getSimpleName(), resolved.schema);
        // Add all nested/referenced schemas
        if (resolved.referencedSchemas != null) {
            schemas.putAll(resolved.referencedSchemas);
        }
    }

    @SuppressWarnings("rawtypes")
    private void sortSchemas(OpenAPI openApi) {
        if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
            return;
        }
        openApi.getComponents().setSchemas(new TreeMap<>(openApi.getComponents().getSchemas()));
    }

    @SuppressWarnings("rawtypes")
    private void cleanupUnreferencedSchemas(OpenAPI openApi) {
        if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
            return;
        }
        Set<String> referenced = collectReferencedSchemas(openApi);
        Map<String, Schema> schemas = openApi.getComponents().getSchemas();
        schemas.keySet().retainAll(referenced);
    }

    @SuppressWarnings("rawtypes")
    private Set<String> collectReferencedSchemas(OpenAPI openApi) {
        Set<String> refs = new HashSet<>();
        if (openApi.getPaths() == null) {
            return refs;
        }
        // Collect direct $ref targets from all path operations
        for (PathItem pathItem : openApi.getPaths().values()) {
            for (Operation op : pathItem.readOperations()) {
                // Request body
                if (op.getRequestBody() != null && op.getRequestBody().getContent() != null) {
                    collectRefsFromContent(op.getRequestBody().getContent(), refs);
                }
                // Responses
                if (op.getResponses() != null) {
                    for (ApiResponse resp : op.getResponses().values()) {
                        if (resp.getContent() != null) {
                            collectRefsFromContent(resp.getContent(), refs);
                        }
                    }
                }
            }
        }
        // Transitively resolve nested $refs from schemas
        Map<String, Schema> schemas = openApi.getComponents() != null
                ? openApi.getComponents().getSchemas() : null;
        if (schemas != null) {
            Set<String> queue = new HashSet<>(refs);
            while (!queue.isEmpty()) {
                Set<String> next = new HashSet<>();
                for (String name : queue) {
                    Schema schema = schemas.get(name);
                    if (schema != null) {
                        Set<String> nested = new HashSet<>();
                        collectRefsFromSchema(schema, nested);
                        for (String n : nested) {
                            if (refs.add(n)) {
                                next.add(n);
                            }
                        }
                    }
                }
                queue = next;
            }
        }
        return refs;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void collectRefsFromContent(Content content, Set<String> refs) {
        if (content == null) return;
        for (MediaType mt : content.values()) {
            if (mt.getSchema() != null) {
                collectRefsFromSchema(mt.getSchema(), refs);
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void collectRefsFromSchema(Schema schema, Set<String> refs) {
        if (schema == null) return;
        // Direct $ref
        if (schema.get$ref() != null) {
            String refName = extractSchemaName(schema.get$ref());
            if (refName != null) {
                refs.add(refName);
            }
        }
        // Properties
        if (schema.getProperties() != null) {
            for (Schema prop : ((Map<String, Schema>) schema.getProperties()).values()) {
                collectRefsFromSchema(prop, refs);
            }
        }
        // Items (array schemas)
        if (schema.getItems() != null) {
            collectRefsFromSchema(schema.getItems(), refs);
        }
        // Composition: allOf, oneOf, anyOf
        if (schema.getAllOf() != null) {
            for (Schema s : (List<Schema>) schema.getAllOf()) {
                collectRefsFromSchema(s, refs);
            }
        }
        if (schema.getOneOf() != null) {
            for (Schema s : (List<Schema>) schema.getOneOf()) {
                collectRefsFromSchema(s, refs);
            }
        }
        if (schema.getAnyOf() != null) {
            for (Schema s : (List<Schema>) schema.getAnyOf()) {
                collectRefsFromSchema(s, refs);
            }
        }
        // additionalProperties (when it's a Schema)
        if (schema.getAdditionalProperties() instanceof Schema) {
            collectRefsFromSchema((Schema) schema.getAdditionalProperties(), refs);
        }
    }

    private String extractSchemaName(String ref) {
        if (ref == null) return null;
        String prefix = "#/components/schemas/";
        if (ref.startsWith(prefix)) {
            return ref.substring(prefix.length());
        }
        return null;
    }
}
