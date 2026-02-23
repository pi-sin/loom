package io.loom.ui;

import io.loom.core.engine.Dag;
import io.loom.core.engine.DagNode;
import io.loom.core.model.ApiDefinition;
import io.loom.core.registry.ApiRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

import java.util.ArrayList;
import java.util.List;

@RestController
public class LoomUiController {

    private final ApiRegistry apiRegistry;

    public LoomUiController(ApiRegistry apiRegistry) {
        this.apiRegistry = apiRegistry;
    }

    @GetMapping("/loom/ui")
    public RedirectView redirectToUi() {
        return new RedirectView("/loom/index.html");
    }

    @GetMapping("/loom/api/graphs")
    public List<ApiGraphDto> getGraphs() {
        List<ApiGraphDto> graphs = new ArrayList<>();

        for (ApiDefinition api : apiRegistry.getAllApis()) {
            if (api.isPassthrough()) {
                graphs.add(new ApiGraphDto(
                        api.method(),
                        api.path(),
                        api.requestType() != null && api.requestType() != void.class
                                ? api.requestType().getSimpleName() : null,
                        api.responseType() != null && api.responseType() != void.class
                                ? api.responseType().getSimpleName() : null,
                        List.of(new NodeDto("passthrough",
                                api.upstreamName() + api.upstreamPath(),
                                true, 0, true)),
                        List.of(),
                        "passthrough"
                ));
            } else {
                Dag dag = api.dag();
                List<NodeDto> nodes = new ArrayList<>();
                List<EdgeDto> edges = new ArrayList<>();

                for (var entry : dag.getNodes().entrySet()) {
                    DagNode node = entry.getValue();
                    boolean isTerminal = node.builderClass().equals(dag.getTerminalNode().builderClass());

                    nodes.add(new NodeDto(
                            node.name(),
                            node.outputType().getSimpleName(),
                            node.required(),
                            node.timeoutMs(),
                            isTerminal
                    ));

                    for (var dep : node.dependsOn()) {
                        DagNode depNode = dag.getNode(dep);
                        if (depNode != null) {
                            edges.add(new EdgeDto(depNode.name(), node.name()));
                        }
                    }
                }

                graphs.add(new ApiGraphDto(
                        api.method(),
                        api.path(),
                        api.requestType() != void.class ? api.requestType().getSimpleName() : null,
                        api.responseType().getSimpleName(),
                        nodes,
                        edges,
                        "builder"
                ));
            }
        }

        return graphs;
    }

    public record ApiGraphDto(
            String method,
            String path,
            String requestType,
            String responseType,
            List<NodeDto> nodes,
            List<EdgeDto> edges,
            String type
    ) {}

    public record NodeDto(
            String name,
            String outputType,
            boolean required,
            long timeoutMs,
            boolean terminal
    ) {}

    public record EdgeDto(
            String from,
            String to
    ) {}
}
