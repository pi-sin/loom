package io.loom.core.exception;

import java.util.List;
import java.util.Map;

public class LoomValidationException extends LoomException {
    private final Map<String, List<String>> violations;

    public LoomValidationException(Map<String, List<String>> violations) {
        super("Validation failed: " + violations);
        this.violations = violations;
    }

    public Map<String, List<String>> getViolations() {
        return violations;
    }
}
