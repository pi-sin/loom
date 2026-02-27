package io.loom.core.exception;

import lombok.Getter;

@Getter
public class LoomBuilderTimeoutException extends LoomException {
    private final String builderName;
    private final long timeoutMs;

    public LoomBuilderTimeoutException(String builderName, long timeoutMs) {
        super("Builder '" + builderName + "' timed out after " + timeoutMs + "ms");
        this.builderName = builderName;
        this.timeoutMs = timeoutMs;
    }
}
