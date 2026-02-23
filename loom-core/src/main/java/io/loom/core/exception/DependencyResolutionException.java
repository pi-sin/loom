package io.loom.core.exception;

import java.util.List;

import lombok.Getter;

@Getter
public class DependencyResolutionException extends LoomException {

    private final String requestedType;

    private final List<String> availableTypes;

    private final List<String> completedBuilders;

    private static String buildMessage(String requestedType, List<String> availableTypes,
                                       List<String> completedBuilders) {
        return "No dependency found with output type: " + requestedType + ". " + "Available output types: "
                + availableTypes + ". " + "Completed builders: " + completedBuilders + ". "
                + "Possible causes: upstream builder failed, timed out, or dependency not declared in @LoomGraph.";
    }

    public DependencyResolutionException(String requestedType, List<String> availableTypes,
                                         List<String> completedBuilders) {
        super(buildMessage(requestedType, availableTypes, completedBuilders));
        this.requestedType = requestedType;
        this.availableTypes = availableTypes;
        this.completedBuilders = completedBuilders;
    }
}
