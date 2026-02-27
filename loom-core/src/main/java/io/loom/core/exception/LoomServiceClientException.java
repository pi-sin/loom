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
}
