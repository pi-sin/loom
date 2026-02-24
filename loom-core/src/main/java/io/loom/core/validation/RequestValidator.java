package io.loom.core.validation;

import io.loom.core.codec.JsonCodec;
import io.loom.core.exception.LoomValidationException;
import io.loom.core.interceptor.LoomHttpContext;
import io.loom.core.model.HeaderParamDefinition;
import io.loom.core.model.QueryParamDefinition;

import java.util.*;
import java.util.function.Function;
import java.util.logging.Logger;

public final class RequestValidator {

    private static final Logger LOG = Logger.getLogger(RequestValidator.class.getName());

    private static final Set<String> BODY_METHODS = Set.of("POST", "PUT", "PATCH");

    private RequestValidator() {}

    public record ValidationResult(Map<String, String> queryParamDefaults, Object parsedBody) {}

    // ── Compile (startup) ─────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    public static ValidationPlan compile(List<QueryParamDefinition> queryParams,
                                         List<HeaderParamDefinition> headerParams,
                                         Class<?> requestType,
                                         String method) {
        // Required headers
        String[] reqHeaders = null;
        if (headerParams != null && !headerParams.isEmpty()) {
            List<String> names = new ArrayList<>();
            for (HeaderParamDefinition hp : headerParams) {
                if (hp.required()) names.add(hp.name());
            }
            if (!names.isEmpty()) reqHeaders = names.toArray(String[]::new);
        }

        // Required query params, typed params, defaults
        String[] reqQueryNames = null;
        String[] typedNames = null;
        Function<String, ?>[] typedConverters = null;
        String[] typedTypeNames = null;
        String[] defNames = null;
        String[] defValues = null;

        if (queryParams != null && !queryParams.isEmpty()) {
            List<String> requiredList = new ArrayList<>();
            List<String> tNames = new ArrayList<>();
            List<Function<String, ?>> tConverters = new ArrayList<>();
            List<String> tTypeNames = new ArrayList<>();
            List<String> dNames = new ArrayList<>();
            List<String> dValues = new ArrayList<>();

            for (QueryParamDefinition qp : queryParams) {
                if (qp.required()) {
                    requiredList.add(qp.name());
                    if (qp.defaultValue() != null && !qp.defaultValue().isEmpty()) {
                        LOG.warning("[Loom] Query param '" + qp.name()
                                + "' is required=true AND has a defaultValue — the default is unreachable");
                    }
                }

                // Type coercion setup (skip String — it's a no-op)
                if (qp.type() != null && qp.type() != String.class) {
                    Function<String, ?> converter = resolveConverter(qp.type(), qp.name());
                    tNames.add(qp.name());
                    tConverters.add(converter);
                    tTypeNames.add(qp.type().getSimpleName());
                }

                // Defaults
                if (qp.defaultValue() != null && !qp.defaultValue().isEmpty()) {
                    // Validate default is parseable at startup
                    if (qp.type() != null && qp.type() != String.class) {
                        Function<String, ?> converter = resolveConverter(qp.type(), qp.name());
                        try {
                            converter.apply(qp.defaultValue());
                        } catch (Exception e) {
                            throw new IllegalArgumentException(
                                    "[Loom] Default value '" + qp.defaultValue()
                                            + "' for param '" + qp.name()
                                            + "' is not parseable as " + qp.type().getSimpleName(), e);
                        }
                    }
                    dNames.add(qp.name());
                    dValues.add(qp.defaultValue());
                }
            }

            if (!requiredList.isEmpty()) reqQueryNames = requiredList.toArray(String[]::new);
            if (!tNames.isEmpty()) {
                typedNames = tNames.toArray(String[]::new);
                typedConverters = tConverters.toArray(new Function[0]);
                typedTypeNames = tTypeNames.toArray(String[]::new);
            }
            if (!dNames.isEmpty()) {
                defNames = dNames.toArray(String[]::new);
                defValues = dValues.toArray(String[]::new);
            }
        }

        // Body validation
        boolean needsBody = requestType != null && requestType != void.class
                && method != null && BODY_METHODS.contains(method.toUpperCase());
        Class<?> bodyType = needsBody ? requestType : null;

        // Check if anything to validate
        if (reqHeaders == null && reqQueryNames == null && typedNames == null
                && defNames == null && !needsBody) {
            return ValidationPlan.NONE;
        }

        return new ValidationPlan(reqHeaders, reqQueryNames,
                typedNames, typedConverters, typedTypeNames,
                defNames, defValues, needsBody, bodyType);
    }

    // ── Validate (request-time) ───────────────────────────────────────

    public static ValidationResult validate(ValidationPlan plan,
                                            LoomHttpContext httpContext,
                                            JsonCodec jsonCodec) {
        if (!plan.needsValidation()) return null;

        Map<String, List<String>> violations = null;

        // Phase 1: required headers
        if (plan.requiredHeaderNames != null) {
            for (String name : plan.requiredHeaderNames) {
                String val = httpContext.getHeader(name);
                if (val == null || val.isBlank()) {
                    if (violations == null) violations = new LinkedHashMap<>();
                    violations.computeIfAbsent(name, k -> new ArrayList<>())
                            .add("Required header '" + name + "' is missing or blank");
                }
            }
        }

        // Phase 2: required query params
        if (plan.requiredQueryParamNames != null) {
            for (String name : plan.requiredQueryParamNames) {
                String val = httpContext.getQueryParam(name);
                if (val == null) {
                    if (violations == null) violations = new LinkedHashMap<>();
                    violations.computeIfAbsent(name, k -> new ArrayList<>())
                            .add("Required query parameter '" + name + "' is missing");
                }
            }
        }

        // Phase 3: type coercion — skip if we already have required violations (fail-fast)
        if (violations == null && plan.typedParamNames != null) {
            for (int i = 0; i < plan.typedParamNames.length; i++) {
                String name = plan.typedParamNames[i];
                String val = httpContext.getQueryParam(name);
                if (val == null) continue; // absent params handled by required check or defaults
                try {
                    plan.typedParamConverters[i].apply(val);
                } catch (Exception e) {
                    if (violations == null) violations = new LinkedHashMap<>();
                    violations.computeIfAbsent(name, k -> new ArrayList<>())
                            .add("Query parameter '" + name + "' value '" + val
                                    + "' is not a valid " + plan.typedParamTypeNames[i]);
                }
            }
        }

        // Phase 4: body validation
        Object parsedBody = null;
        if (plan.needsBodyValidation) {
            byte[] rawBody = httpContext.getRawRequestBody();
            if (rawBody == null || rawBody.length == 0) {
                if (violations == null) violations = new LinkedHashMap<>();
                violations.computeIfAbsent("body", k -> new ArrayList<>())
                        .add("Request body is required for " + httpContext.getHttpMethod()
                                + " " + httpContext.getRequestPath());
            } else {
                try {
                    parsedBody = jsonCodec.readValue(rawBody, plan.requestBodyType);
                } catch (Exception e) {
                    if (violations == null) violations = new LinkedHashMap<>();
                    violations.computeIfAbsent("body", k -> new ArrayList<>())
                            .add("Invalid request body: " + e.getMessage());
                }
            }
        }

        // Throw if violations
        if (violations != null) {
            throw new LoomValidationException(violations);
        }

        // Phase 5: apply defaults
        Map<String, String> defaults = null;
        if (plan.hasDefaults) {
            for (int i = 0; i < plan.defaultParamNames.length; i++) {
                String val = httpContext.getQueryParam(plan.defaultParamNames[i]);
                if (val == null) {
                    if (defaults == null) defaults = new LinkedHashMap<>();
                    defaults.put(plan.defaultParamNames[i], plan.defaultParamValues[i]);
                }
            }
        }

        if (defaults == null && parsedBody == null) return null;
        return new ValidationResult(defaults, parsedBody);
    }

    // ── Type converters ───────────────────────────────────────────────

    private static Function<String, ?> resolveConverter(Class<?> type, String paramName) {
        if (type == Integer.class || type == int.class) return Integer::parseInt;
        if (type == Long.class || type == long.class) return Long::parseLong;
        if (type == Double.class || type == double.class) return Double::parseDouble;
        if (type == Float.class || type == float.class) return Float::parseFloat;
        if (type == Boolean.class || type == boolean.class) return RequestValidator::parseStrictBoolean;
        throw new IllegalArgumentException(
                "[Loom] Unsupported query param type " + type.getSimpleName()
                        + " for param '" + paramName + "'. Supported: String, Integer, Long, Double, Float, Boolean");
    }

    private static Boolean parseStrictBoolean(String value) {
        if ("true".equalsIgnoreCase(value)) return Boolean.TRUE;
        if ("false".equalsIgnoreCase(value)) return Boolean.FALSE;
        throw new IllegalArgumentException("Not a valid boolean: '" + value + "' (expected 'true' or 'false')");
    }
}
