package io.loom.core.exception;

public class BuilderTimeoutException extends LoomException {
    private final String builderName;
    private final long timeoutMs;

    public BuilderTimeoutException(String builderName, long timeoutMs) {
        super("Builder '" + builderName + "' timed out after " + timeoutMs + "ms");
        this.builderName = builderName;
        this.timeoutMs = timeoutMs;
    }

    public String getBuilderName() {
        return builderName;
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }
}
