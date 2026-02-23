package io.loom.core.exception;

public class LoomException extends RuntimeException {

    private String requestId;
    private String apiRoute;

    public LoomException(String message) {
        super(message);
    }

    public LoomException(String message, Throwable cause) {
        super(message, cause);
    }

    public LoomException withRequestId(String id) {
        this.requestId = id;
        return this;
    }

    public LoomException withApiRoute(String route) {
        this.apiRoute = route;
        return this;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getApiRoute() {
        return apiRoute;
    }

    @Override
    public String getMessage() {
        String base = super.getMessage();
        if (apiRoute != null) base += " [route=" + apiRoute + "]";
        if (requestId != null) base += " [requestId=" + requestId + "]";
        return base;
    }
}
