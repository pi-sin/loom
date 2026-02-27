package io.loom.core.exception;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LoomExceptionHierarchyTest {

    @Test
    void loomExceptionHasStandardConstructors() {
        LoomException noArg = new LoomException();
        assertThat(noArg.getMessage()).isNull();

        LoomException msgOnly = new LoomException("test error");
        assertThat(msgOnly.getMessage()).isEqualTo("test error");

        Throwable cause = new RuntimeException("root cause");
        LoomException msgAndCause = new LoomException("wrapped", cause);
        assertThat(msgAndCause.getMessage()).isEqualTo("wrapped");
        assertThat(msgAndCause.getCause()).isSameAs(cause);

        LoomException causeOnly = new LoomException(cause);
        assertThat(causeOnly.getCause()).isSameAs(cause);
    }

    @Test
    void withApiRouteReturnsSameInstanceForChaining() {
        LoomException ex = new LoomException("test");
        LoomException returned = ex.withApiRoute("GET /api/test");
        assertThat(returned).isSameAs(ex);
    }

    @Test
    void getMessageIncludesApiRouteWhenSet() {
        LoomException ex = new LoomException("something failed");
        ex.withApiRoute("POST /api/orders");
        assertThat(ex.getMessage()).isEqualTo("something failed [route=POST /api/orders]");
    }

    @Test
    void getMessageOmitsApiRouteWhenNull() {
        LoomException ex = new LoomException("something failed");
        assertThat(ex.getMessage()).isEqualTo("something failed");
        assertThat(ex.getMessage()).doesNotContain("[route=");
    }

    @Test
    void serviceClientExceptionPreservesCause() {
        Throwable rootCause = new RuntimeException("connection refused");
        LoomServiceClientException ex = new LoomServiceClientException(
                "payment-svc", 503, "Service Unavailable", rootCause);

        assertThat(ex.getCause()).isSameAs(rootCause);
        assertThat(ex.getMessage()).contains("payment-svc")
                .contains("503")
                .contains("Service Unavailable");
    }

    @Test
    void serviceClientExceptionExposesFieldsViaGetter() {
        LoomServiceClientException ex = new LoomServiceClientException(
                "user-svc", 404, "Not Found");

        assertThat(ex.getServiceName()).isEqualTo("user-svc");
        assertThat(ex.getStatusCode()).isEqualTo(404);
    }

    @Test
    void serviceClientExceptionWithoutStatusCode() {
        Throwable cause = new RuntimeException("timeout");
        LoomServiceClientException ex = new LoomServiceClientException(
                "inventory-svc", "Connection timed out", cause);

        assertThat(ex.getStatusCode()).isEqualTo(-1);
        assertThat(ex.getServiceName()).isEqualTo("inventory-svc");
        assertThat(ex.getCause()).isSameAs(cause);
        assertThat(ex.getMessage()).contains("inventory-svc")
                .contains("failed")
                .contains("Connection timed out");
    }

    @Test
    void builderTimeoutExceptionExposesFieldsViaGetter() {
        LoomBuilderTimeoutException ex = new LoomBuilderTimeoutException("FetchUserBuilder", 3000);

        assertThat(ex.getBuilderName()).isEqualTo("FetchUserBuilder");
        assertThat(ex.getTimeoutMs()).isEqualTo(3000);
        assertThat(ex.getMessage()).contains("FetchUserBuilder")
                .contains("3000ms");
    }

    @Test
    void dependencyResolutionExceptionExposesFieldsViaGetter() {
        List<String> available = List.of("String", "Integer");
        List<String> completed = List.of("FastBuilder", "SlowBuilder");
        LoomDependencyResolutionException ex = new LoomDependencyResolutionException(
                "Dashboard", available, completed);

        assertThat(ex.getRequestedType()).isEqualTo("Dashboard");
        assertThat(ex.getAvailableTypes()).containsExactly("String", "Integer");
        assertThat(ex.getCompletedBuilders()).containsExactly("FastBuilder", "SlowBuilder");
        assertThat(ex.getMessage()).contains("Dashboard")
                .contains("Available output types:")
                .contains("Completed builders:");
    }

    @Test
    void cycleDetectedExceptionExposesFieldsViaGetter() {
        List<String> cycle = List.of("A", "B", "C", "A");
        LoomCycleDetectedException ex = new LoomCycleDetectedException(cycle);

        assertThat(ex.getCycle()).containsExactly("A", "B", "C", "A");
        assertThat(ex.getMessage()).contains("Cycle detected in DAG: A -> B -> C -> A");
    }

    @Test
    void unknownDependencyExceptionExposesFieldsViaGetter() {
        LoomUnknownDependencyException ex = new LoomUnknownDependencyException(
                "EnrichUser", "FetchProfile");

        assertThat(ex.getNodeName()).isEqualTo("EnrichUser");
        assertThat(ex.getDependencyName()).isEqualTo("FetchProfile");
        assertThat(ex.getMessage()).contains("EnrichUser")
                .contains("FetchProfile");
    }

    @Test
    void validationExceptionExposesFieldsViaGetter() {
        Map<String, List<String>> violations = Map.of(
                "email", List.of("must not be blank"),
                "age", List.of("must be positive", "must be less than 150")
        );
        LoomValidationException ex = new LoomValidationException(violations);

        assertThat(ex.getViolations()).containsKey("email");
        assertThat(ex.getViolations()).containsKey("age");
        assertThat(ex.getViolations().get("age")).hasSize(2);
        assertThat(ex.getMessage()).contains("Validation failed");
    }

    @Test
    void routeNotFoundExceptionMessage() {
        LoomRouteNotFoundException ex = new LoomRouteNotFoundException("product-svc", "get-by-id");

        assertThat(ex.getMessage()).isEqualTo("Route 'get-by-id' not found for service 'product-svc'");
    }

    @Test
    void allExceptionsExtendLoomException() {
        assertThat(new LoomServiceClientException("svc", 500, "err"))
                .isInstanceOf(LoomException.class);
        assertThat(new LoomBuilderTimeoutException("builder", 1000))
                .isInstanceOf(LoomException.class);
        assertThat(new LoomDependencyResolutionException("Type", List.of(), List.of()))
                .isInstanceOf(LoomException.class);
        assertThat(new LoomCycleDetectedException(List.of("A", "B")))
                .isInstanceOf(LoomException.class);
        assertThat(new LoomUnknownDependencyException("node", "dep"))
                .isInstanceOf(LoomException.class);
        assertThat(new LoomValidationException(Map.of()))
                .isInstanceOf(LoomException.class);
        assertThat(new LoomRouteNotFoundException("svc", "route"))
                .isInstanceOf(LoomException.class);
    }

    @Test
    void noGetRequestIdMethodExists() throws Exception {
        try {
            LoomException.class.getMethod("getRequestId");
            throw new AssertionError("getRequestId() method should not exist on LoomException");
        } catch (NoSuchMethodException expected) {
            // Correct — method was removed
        }
    }
}
