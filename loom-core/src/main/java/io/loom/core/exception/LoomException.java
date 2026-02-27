package io.loom.core.exception;

import lombok.experimental.StandardException;

@StandardException
public class LoomException extends RuntimeException {

    private String apiRoute;

    public LoomException withApiRoute(String route) {
        this.apiRoute = route;
        return this;
    }

    public String getApiRoute() {
        return apiRoute;
    }

    @Override
    public String getMessage() {
        String base = super.getMessage();
        if (apiRoute != null) base += " [route=" + apiRoute + "]";
        return base;
    }
}
