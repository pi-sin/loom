package io.loom.core.model;

import io.loom.core.engine.DagNode;
import java.util.List;

public record GraphDefinition(
    String apiPath,
    String apiMethod,
    List<DagNode> nodes,
    Class<?> terminalOutputType
) {}
