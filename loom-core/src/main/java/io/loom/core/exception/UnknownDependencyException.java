package io.loom.core.exception;

public class UnknownDependencyException extends LoomException {
    private final String nodeName;
    private final String dependencyName;

    public UnknownDependencyException(String nodeName, String dependencyName) {
        super("Node '" + nodeName + "' depends on unknown node '" + dependencyName + "'");
        this.nodeName = nodeName;
        this.dependencyName = dependencyName;
    }

    public String getNodeName() {
        return nodeName;
    }

    public String getDependencyName() {
        return dependencyName;
    }
}
