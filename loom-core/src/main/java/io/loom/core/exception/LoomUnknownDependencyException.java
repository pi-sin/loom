package io.loom.core.exception;

import lombok.Getter;

@Getter
public class LoomUnknownDependencyException extends LoomException {
    private final String nodeName;
    private final String dependencyName;

    public LoomUnknownDependencyException(String nodeName, String dependencyName) {
        super("Node '" + nodeName + "' depends on unknown node '" + dependencyName + "'");
        this.nodeName = nodeName;
        this.dependencyName = dependencyName;
    }
}
