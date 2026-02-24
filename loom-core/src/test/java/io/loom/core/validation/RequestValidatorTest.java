package io.loom.core.validation;

import io.loom.core.codec.DslJsonCodec;
import io.loom.core.codec.JsonCodec;
import io.loom.core.exception.LoomValidationException;
import io.loom.core.interceptor.LoomHttpContext;
import io.loom.core.model.HeaderParamDefinition;
import io.loom.core.model.QueryParamDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class RequestValidatorTest {

    private final JsonCodec jsonCodec = new DslJsonCodec();

    // ── compile: no-validation fast path ──────────────────────────────

    @Test
    void noParams_returnsNoneplan() {
        ValidationPlan plan = RequestValidator.compile(List.of(), List.of(), void.class, "GET");
        assertThat(plan.needsValidation()).isFalse();
    }

    @Test
    void nullLists_returnsNonePlan() {
        ValidationPlan plan = RequestValidator.compile(null, null, null, "GET");
        assertThat(plan.needsValidation()).isFalse();
    }

    @Test
    void validateOnNonePlan_returnsNull() {
        ValidationPlan plan = ValidationPlan.NONE;
        var result = RequestValidator.validate(plan, stubContext("GET", null, Map.of(), Map.of()), jsonCodec);
        assertThat(result).isNull();
    }

    // ── Required headers ──────────────────────────────────────────────

    @Test
    void requiredHeaderMissing_throwsValidation() {
        ValidationPlan plan = RequestValidator.compile(
                List.of(),
                List.of(new HeaderParamDefinition("X-API-Key", true, "")),
                void.class, "GET");

        assertThat(plan.needsValidation()).isTrue();

        assertThatThrownBy(() -> RequestValidator.validate(plan,
                        stubContext("GET", null, Map.of(), Map.of()), jsonCodec))
                .isInstanceOf(LoomValidationException.class)
                .satisfies(ex -> {
                    var violations = ((LoomValidationException) ex).getViolations();
                    assertThat(violations).containsKey("X-API-Key");
                });
    }

    @Test
    void requiredHeaderBlank_throwsValidation() {
        ValidationPlan plan = RequestValidator.compile(
                List.of(),
                List.of(new HeaderParamDefinition("X-API-Key", true, "")),
                void.class, "GET");

        assertThatThrownBy(() -> RequestValidator.validate(plan,
                        stubContext("GET", null, Map.of(), Map.of("X-API-Key", "   ")), jsonCodec))
                .isInstanceOf(LoomValidationException.class);
    }

    @Test
    void requiredHeaderPresent_passes() {
        ValidationPlan plan = RequestValidator.compile(
                List.of(),
                List.of(new HeaderParamDefinition("X-API-Key", true, "")),
                void.class, "GET");

        var result = RequestValidator.validate(plan,
                stubContext("GET", null, Map.of(), Map.of("X-API-Key", "secret")), jsonCodec);
        assertThat(result).isNull();
    }

    // ── Required query params ─────────────────────────────────────────

    @Test
    void requiredQueryParamMissing_throwsValidation() {
        ValidationPlan plan = RequestValidator.compile(
                List.of(new QueryParamDefinition("page", String.class, true, "", "")),
                List.of(), void.class, "GET");

        assertThatThrownBy(() -> RequestValidator.validate(plan,
                        stubContext("GET", null, Map.of(), Map.of()), jsonCodec))
                .isInstanceOf(LoomValidationException.class)
                .satisfies(ex -> {
                    var violations = ((LoomValidationException) ex).getViolations();
                    assertThat(violations).containsKey("page");
                });
    }

    @Test
    void requiredQueryParamPresent_passes() {
        ValidationPlan plan = RequestValidator.compile(
                List.of(new QueryParamDefinition("page", String.class, true, "", "")),
                List.of(), void.class, "GET");

        var result = RequestValidator.validate(plan,
                stubContext("GET", null, Map.of("page", "1"), Map.of()), jsonCodec);
        assertThat(result).isNull();
    }

    // ── Type coercion ─────────────────────────────────────────────────

    @Test
    void integerCoercion_validValue_passes() {
        ValidationPlan plan = RequestValidator.compile(
                List.of(new QueryParamDefinition("limit", Integer.class, false, "", "")),
                List.of(), void.class, "GET");

        var result = RequestValidator.validate(plan,
                stubContext("GET", null, Map.of("limit", "10"), Map.of()), jsonCodec);
        // no exception = pass
        assertThat(result).isNull();
    }

    @Test
    void integerCoercion_invalidValue_throwsValidation() {
        ValidationPlan plan = RequestValidator.compile(
                List.of(new QueryParamDefinition("limit", Integer.class, false, "", "")),
                List.of(), void.class, "GET");

        assertThatThrownBy(() -> RequestValidator.validate(plan,
                        stubContext("GET", null, Map.of("limit", "abc"), Map.of()), jsonCodec))
                .isInstanceOf(LoomValidationException.class)
                .satisfies(ex -> {
                    var violations = ((LoomValidationException) ex).getViolations();
                    assertThat(violations).containsKey("limit");
                    assertThat(violations.get("limit").get(0)).contains("Integer");
                });
    }

    @Test
    void longCoercion_validValue_passes() {
        ValidationPlan plan = RequestValidator.compile(
                List.of(new QueryParamDefinition("offset", Long.class, false, "", "")),
                List.of(), void.class, "GET");

        var result = RequestValidator.validate(plan,
                stubContext("GET", null, Map.of("offset", "999999999999"), Map.of()), jsonCodec);
        assertThat(result).isNull();
    }

    @Test
    void doubleCoercion_invalidValue_throwsValidation() {
        ValidationPlan plan = RequestValidator.compile(
                List.of(new QueryParamDefinition("price", Double.class, false, "", "")),
                List.of(), void.class, "GET");

        assertThatThrownBy(() -> RequestValidator.validate(plan,
                        stubContext("GET", null, Map.of("price", "notanumber"), Map.of()), jsonCodec))
                .isInstanceOf(LoomValidationException.class);
    }

    @Test
    void floatCoercion_validValue_passes() {
        ValidationPlan plan = RequestValidator.compile(
                List.of(new QueryParamDefinition("ratio", Float.class, false, "", "")),
                List.of(), void.class, "GET");

        var result = RequestValidator.validate(plan,
                stubContext("GET", null, Map.of("ratio", "1.5"), Map.of()), jsonCodec);
        assertThat(result).isNull();
    }

    @Test
    void strictBoolean_validValues_pass() {
        ValidationPlan plan = RequestValidator.compile(
                List.of(new QueryParamDefinition("active", Boolean.class, false, "", "")),
                List.of(), void.class, "GET");

        // "true" passes
        assertThatCode(() -> RequestValidator.validate(plan,
                stubContext("GET", null, Map.of("active", "true"), Map.of()), jsonCodec))
                .doesNotThrowAnyException();

        // "false" passes
        assertThatCode(() -> RequestValidator.validate(plan,
                stubContext("GET", null, Map.of("active", "false"), Map.of()), jsonCodec))
                .doesNotThrowAnyException();

        // "TRUE" passes (case-insensitive)
        assertThatCode(() -> RequestValidator.validate(plan,
                stubContext("GET", null, Map.of("active", "TRUE"), Map.of()), jsonCodec))
                .doesNotThrowAnyException();
    }

    @Test
    void strictBoolean_invalidValue_throwsValidation() {
        ValidationPlan plan = RequestValidator.compile(
                List.of(new QueryParamDefinition("active", Boolean.class, false, "", "")),
                List.of(), void.class, "GET");

        assertThatThrownBy(() -> RequestValidator.validate(plan,
                        stubContext("GET", null, Map.of("active", "banana"), Map.of()), jsonCodec))
                .isInstanceOf(LoomValidationException.class)
                .satisfies(ex -> {
                    var violations = ((LoomValidationException) ex).getViolations();
                    assertThat(violations).containsKey("active");
                    assertThat(violations.get("active").get(0)).contains("Boolean");
                });
    }

    // ── Fail-fast: required violations skip type coercion ─────────────

    @Test
    void failFast_requiredViolationsSkipTypeCoercion() {
        // "limit" is required and missing, "limit" is also typed Integer
        // A separate non-required param "count" has a bad value
        // If fail-fast works, only the required violation is reported
        ValidationPlan plan = RequestValidator.compile(
                List.of(
                        new QueryParamDefinition("limit", Integer.class, true, "", ""),
                        new QueryParamDefinition("count", Integer.class, false, "", "")
                ),
                List.of(), void.class, "GET");

        assertThatThrownBy(() -> RequestValidator.validate(plan,
                        stubContext("GET", null, Map.of("count", "bad"), Map.of()), jsonCodec))
                .isInstanceOf(LoomValidationException.class)
                .satisfies(ex -> {
                    var violations = ((LoomValidationException) ex).getViolations();
                    assertThat(violations).containsKey("limit");
                    assertThat(violations).doesNotContainKey("count"); // type coercion skipped
                });
    }

    // ── Multiple violations collected in single exception ─────────────

    @Test
    void multipleViolations_collectedInSingleException() {
        ValidationPlan plan = RequestValidator.compile(
                List.of(
                        new QueryParamDefinition("page", String.class, true, "", ""),
                        new QueryParamDefinition("limit", String.class, true, "", "")
                ),
                List.of(new HeaderParamDefinition("X-Token", true, "")),
                void.class, "GET");

        assertThatThrownBy(() -> RequestValidator.validate(plan,
                        stubContext("GET", null, Map.of(), Map.of()), jsonCodec))
                .isInstanceOf(LoomValidationException.class)
                .satisfies(ex -> {
                    var violations = ((LoomValidationException) ex).getViolations();
                    assertThat(violations).containsKeys("X-Token", "page", "limit");
                });
    }

    // ── Defaults ──────────────────────────────────────────────────────

    @Test
    void defaultApplied_whenParamAbsent() {
        ValidationPlan plan = RequestValidator.compile(
                List.of(new QueryParamDefinition("limit", Integer.class, false, "10", "")),
                List.of(), void.class, "GET");

        var result = RequestValidator.validate(plan,
                stubContext("GET", null, Map.of(), Map.of()), jsonCodec);
        assertThat(result).isNotNull();
        assertThat(result.queryParamDefaults()).containsEntry("limit", "10");
    }

    @Test
    void defaultNotApplied_whenParamPresent() {
        ValidationPlan plan = RequestValidator.compile(
                List.of(new QueryParamDefinition("limit", Integer.class, false, "10", "")),
                List.of(), void.class, "GET");

        var result = RequestValidator.validate(plan,
                stubContext("GET", null, Map.of("limit", "50"), Map.of()), jsonCodec);
        // defaults map should be null since param is present
        assertThat(result).isNull();
    }

    // ── compile: startup validation of defaults ───────────────────────

    @Test
    void unparsableDefault_throwsAtStartup() {
        assertThatThrownBy(() -> RequestValidator.compile(
                List.of(new QueryParamDefinition("limit", Integer.class, false, "notanint", "")),
                List.of(), void.class, "GET"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("notanint")
                .hasMessageContaining("Integer");
    }

    // ── Body validation ───────────────────────────────────────────────

    @Test
    void bodyMissing_forPost_throwsValidation() {
        ValidationPlan plan = RequestValidator.compile(
                List.of(), List.of(), TestBody.class, "POST");

        assertThat(plan.needsValidation()).isTrue();

        assertThatThrownBy(() -> RequestValidator.validate(plan,
                        stubContext("POST", new byte[0], Map.of(), Map.of()), jsonCodec))
                .isInstanceOf(LoomValidationException.class)
                .satisfies(ex -> {
                    var violations = ((LoomValidationException) ex).getViolations();
                    assertThat(violations).containsKey("body");
                });
    }

    @Test
    void bodyMalformedJson_throwsValidation() {
        ValidationPlan plan = RequestValidator.compile(
                List.of(), List.of(), TestBody.class, "POST");

        assertThatThrownBy(() -> RequestValidator.validate(plan,
                        stubContext("POST", "{bad json".getBytes(), Map.of(), Map.of()), jsonCodec))
                .isInstanceOf(LoomValidationException.class)
                .satisfies(ex -> {
                    var violations = ((LoomValidationException) ex).getViolations();
                    assertThat(violations).containsKey("body");
                });
    }

    @Test
    void bodyValid_returnsParsedObjectInResult() {
        ValidationPlan plan = RequestValidator.compile(
                List.of(), List.of(), TestBody.class, "POST");

        var result = RequestValidator.validate(plan,
                stubContext("POST", "{\"name\":\"test\"}".getBytes(), Map.of(), Map.of()), jsonCodec);

        assertThat(result).isNotNull();
        assertThat(result.parsedBody()).isInstanceOf(TestBody.class);
        assertThat(((TestBody) result.parsedBody()).name()).isEqualTo("test");
    }

    @Test
    void getWithRequestType_noBodyValidation() {
        ValidationPlan plan = RequestValidator.compile(
                List.of(), List.of(), TestBody.class, "GET");

        // GET with requestType should NOT trigger body validation
        assertThat(plan.needsBodyValidation).isFalse();
        var result = RequestValidator.validate(plan,
                stubContext("GET", null, Map.of(), Map.of()), jsonCodec);
        assertThat(result).isNull();
    }

    @Test
    void postWithVoidRequestType_noBodyValidation() {
        ValidationPlan plan = RequestValidator.compile(
                List.of(), List.of(), void.class, "POST");

        assertThat(plan.needsValidation()).isFalse();
    }

    @Test
    void putMethod_triggersBodyValidation() {
        ValidationPlan plan = RequestValidator.compile(
                List.of(), List.of(), TestBody.class, "PUT");

        assertThat(plan.needsBodyValidation).isTrue();
    }

    @Test
    void patchMethod_triggersBodyValidation() {
        ValidationPlan plan = RequestValidator.compile(
                List.of(), List.of(), TestBody.class, "PATCH");

        assertThat(plan.needsBodyValidation).isTrue();
    }

    // ── Absent typed param (not required, no value) is not an error ───

    @Test
    void absentTypedParam_notRequired_noError() {
        ValidationPlan plan = RequestValidator.compile(
                List.of(new QueryParamDefinition("limit", Integer.class, false, "", "")),
                List.of(), void.class, "GET");

        // no "limit" param at all — should be fine
        assertThatCode(() -> RequestValidator.validate(plan,
                stubContext("GET", null, Map.of(), Map.of()), jsonCodec))
                .doesNotThrowAnyException();
    }

    // ── Test helper types ─────────────────────────────────────────────

    public record TestBody(String name) {}

    // ── Stub LoomHttpContext ──────────────────────────────────────────

    private static LoomHttpContext stubContext(String method, byte[] body,
                                              Map<String, String> queryParams,
                                              Map<String, String> headers) {
        return new LoomHttpContext() {
            @Override public String getHttpMethod() { return method; }
            @Override public String getRequestPath() { return "/test"; }
            @Override public String getHeader(String name) { return headers.get(name); }
            @Override public Map<String, List<String>> getHeaders() {
                Map<String, List<String>> result = new java.util.LinkedHashMap<>();
                headers.forEach((k, v) -> result.put(k, List.of(v)));
                return result;
            }
            @Override public String getQueryParam(String name) { return queryParams.get(name); }
            @Override public Map<String, List<String>> getQueryParams() {
                Map<String, List<String>> result = new java.util.LinkedHashMap<>();
                queryParams.forEach((k, v) -> result.put(k, List.of(v)));
                return result;
            }
            @Override public String getPathVariable(String name) { return null; }
            @Override public Map<String, String> getPathVariables() { return Map.of(); }
            @Override public byte[] getRawRequestBody() { return body; }
            @Override public <T> T getRequestBody(Class<T> type) { return null; }
            @Override public void setAttribute(String key, Object value) {}
            @Override public <T> T getAttribute(String key, Class<T> type) { return null; }
            @Override public Map<String, Object> getAttributes() { return Map.of(); }
            @Override public void setResponseStatus(int status) {}
            @Override public void setResponseHeader(String name, String value) {}
            @Override public void setResponseBody(Object body) {}
            @Override public int getResponseStatus() { return 200; }
            @Override public Object getResponseBody() { return null; }
            @Override public String getRequestId() { return "test-id"; }
        };
    }
}
