package io.loom.core.exception;

import lombok.Getter;

@Getter
public class LoomServiceClientException extends LoomException {
    private final String serviceName;
    private final int statusCode;

    public LoomServiceClientException(String serviceName, int statusCode, String message) {
        super("Service '" + serviceName + "' returned status " + statusCode + ": " + message);
        this.serviceName = serviceName;
        this.statusCode = statusCode;
    }

    public LoomServiceClientException(String serviceName, int statusCode, String message, Throwable cause) {
        super("Service '" + serviceName + "' returned status " + statusCode + ": " + message, cause);
        this.serviceName = serviceName;
        this.statusCode = statusCode;
    }

    public LoomServiceClientException(String serviceName, String message, Throwable cause) {
        super("Service '" + serviceName + "' failed: " + message, cause);
        this.serviceName = serviceName;
        this.statusCode = -1;
    }

    /**
     * Transport failures (statusCode == -1) and server errors (5xx) are retryable.
     * Client errors (4xx) are never retryable — the same request will produce the same result.
     */
    public boolean isRetryable() {
        return statusCode == -1 || statusCode >= 500;
    }
}
