package io.loom.ui;

import io.loom.core.engine.Dag;
import io.loom.core.engine.DagNode;
import io.loom.core.interceptor.LoomInterceptor;
import io.loom.core.model.ApiDefinition;
import io.loom.core.registry.ApiRegistry;
import io.loom.starter.registry.InterceptorRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

import java.util.ArrayList;
import java.util.List;

@RestController
public class LoomUiController {

    private final ApiRegistry apiRegistry;
    private final InterceptorRegistry interceptorRegistry;

    public LoomUiController(ApiRegistry apiRegistry, InterceptorRegistry interceptorRegistry) {
        this.apiRegistry = apiRegistry;
        this.interceptorRegistry = interceptorRegistry;
    }

    @GetMapping("/loom/ui")
    public RedirectView redirectToUi() {
        return new RedirectView("/loom/index.html");
    }

    @GetMapping("/loom/api/graphs")
    public List<ApiGraphDto> getGraphs() {
        List<ApiGraphDto> graphs = new ArrayList<>();

        for (ApiDefinition api : apiRegistry.getAllApis()) {
            List<InterceptorDto> interceptors = resolveInterceptors(api);

            if (api.isPassthrough()) {
                graphs.add(new ApiGraphDto(
                        api.method(),
                        api.path(),
                        api.requestType() != null && api.requestType() != void.class
                                ? api.requestType().getSimpleName() : null,
                        api.responseType() != null && api.responseType() != void.class
                                ? api.responseType().getSimpleName() : null,
                        List.of(new NodeDto("passthrough",
                                api.serviceName() + api.servicePath(),
                                true, 0, true)),
                        List.of(),
                        "passthrough",
                        interceptors
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
                        "builder",
                        interceptors
                ));
            }
        }

        return graphs;
    }

    private List<InterceptorDto> resolveInterceptors(ApiDefinition api) {
        List<LoomInterceptor> interceptors = interceptorRegistry.getInterceptors(api.interceptors());
        return interceptors.stream()
                .map(i -> new InterceptorDto(i.getClass().getSimpleName(), i.order()))
                .toList();
    }

    public record ApiGraphDto(
            String method,
            String path,
            String requestType,
            String responseType,
            List<NodeDto> nodes,
            List<EdgeDto> edges,
            String type,
            List<InterceptorDto> interceptors
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

    public record InterceptorDto(
            String name,
            int order
    ) {}
}
