package io.loom.core.validation;

import java.util.function.Function;

/**
 * Pre-computed validation plan for an API endpoint.
 * All reflection and type resolution happens at startup; request-time validation
 * uses only primitive array lookups and pre-resolved converters.
 */
public final class ValidationPlan {

    public static final ValidationPlan NONE = new ValidationPlan();

    final boolean needsValidation;

    // Required headers
    final String[] requiredHeaderNames;

    // Required query params
    final String[] requiredQueryParamNames;

    // Typed query params (parallel arrays)
    final String[] typedParamNames;
    final Function<String, ?>[] typedParamConverters;
    final String[] typedParamTypeNames;

    // Defaults (parallel arrays)
    final boolean hasDefaults;
    final String[] defaultParamNames;
    final String[] defaultParamValues;

    // Body validation
    final boolean needsBodyValidation;
    final Class<?> requestBodyType;

    /** No-validation sentinel */
    private ValidationPlan() {
        this.needsValidation = false;
        this.requiredHeaderNames = null;
        this.requiredQueryParamNames = null;
        this.typedParamNames = null;
        this.typedParamConverters = null;
        this.typedParamTypeNames = null;
        this.hasDefaults = false;
        this.defaultParamNames = null;
        this.defaultParamValues = null;
        this.needsBodyValidation = false;
        this.requestBodyType = null;
    }

    @SuppressWarnings("unchecked")
    ValidationPlan(String[] requiredHeaderNames,
                   String[] requiredQueryParamNames,
                   String[] typedParamNames,
                   Function<String, ?>[] typedParamConverters,
                   String[] typedParamTypeNames,
                   String[] defaultParamNames,
                   String[] defaultParamValues,
                   boolean needsBodyValidation,
                   Class<?> requestBodyType) {
        this.requiredHeaderNames = requiredHeaderNames;
        this.requiredQueryParamNames = requiredQueryParamNames;
        this.typedParamNames = typedParamNames;
        this.typedParamConverters = typedParamConverters;
        this.typedParamTypeNames = typedParamTypeNames;
        this.hasDefaults = defaultParamNames != null && defaultParamNames.length > 0;
        this.defaultParamNames = defaultParamNames;
        this.defaultParamValues = defaultParamValues;
        this.needsBodyValidation = needsBodyValidation;
        this.requestBodyType = requestBodyType;

        this.needsValidation = requiredHeaderNames != null
                || requiredQueryParamNames != null
                || typedParamNames != null
                || hasDefaults
                || needsBodyValidation;
    }

    public boolean needsValidation() {
        return needsValidation;
    }
}
