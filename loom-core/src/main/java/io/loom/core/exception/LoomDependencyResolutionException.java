package io.loom.core.exception;

import java.util.List;

import lombok.Getter;

@Getter
public class LoomDependencyResolutionException extends LoomException {

    private final String requestedType;

    private final List<String> availableTypes;

    private final List<String> completedBuilders;

    private static String buildMessage(String requestedType, List<String> availableTypes,
                                       List<String> completedBuilders) {
        return "No dependency found with output type: " + requestedType + ". " + "Available output types: "
                + availableTypes + ". " + "Completed builders: " + completedBuilders + ". "
                + "Possible causes: dependent builder failed, timed out, or dependency not declared in @LoomGraph.";
    }

    public LoomDependencyResolutionException(String requestedType, List<String> availableTypes,
                                         List<String> completedBuilders) {
        super(buildMessage(requestedType, availableTypes, completedBuilders));
        this.requestedType = requestedType;
        this.availableTypes = availableTypes;
        this.completedBuilders = completedBuilders;
    }
}
