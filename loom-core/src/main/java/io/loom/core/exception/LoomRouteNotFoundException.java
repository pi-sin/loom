package io.loom.core.exception;

public class LoomRouteNotFoundException extends LoomException {

    public LoomRouteNotFoundException(String serviceName, String routeName) {
        super("Route '" + routeName + "' not found for service '" + serviceName + "'");
    }
}
