package io.loom.core.exception;

public class ServiceClientException extends LoomException {
    private final String serviceName;
    private final int statusCode;

    public ServiceClientException(String serviceName, int statusCode, String message) {
        super("Service '" + serviceName + "' returned status " + statusCode + ": " + message);
        this.serviceName = serviceName;
        this.statusCode = statusCode;
    }

    public ServiceClientException(String serviceName, String message, Throwable cause) {
        super("Service '" + serviceName + "' failed: " + message, cause);
        this.serviceName = serviceName;
        this.statusCode = -1;
    }

    public String getServiceName() {
        return serviceName;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
