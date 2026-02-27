package io.loom.core.exception;

import java.util.List;
import java.util.Map;

import lombok.Getter;

@Getter
public class LoomValidationException extends LoomException {
    private final Map<String, List<String>> violations;

    public LoomValidationException(Map<String, List<String>> violations) {
        super("Validation failed: " + violations);
        this.violations = Map.copyOf(violations);
    }
}
