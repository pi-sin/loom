package io.loom.core.exception;

public class UpstreamException extends LoomException {
    private final String upstreamName;
    private final int statusCode;

    public UpstreamException(String upstreamName, int statusCode, String message) {
        super("Upstream '" + upstreamName + "' returned status " + statusCode + ": " + message);
        this.upstreamName = upstreamName;
        this.statusCode = statusCode;
    }

    public UpstreamException(String upstreamName, String message, Throwable cause) {
        super("Upstream '" + upstreamName + "' failed: " + message, cause);
        this.upstreamName = upstreamName;
        this.statusCode = -1;
    }

    public String getUpstreamName() {
        return upstreamName;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
