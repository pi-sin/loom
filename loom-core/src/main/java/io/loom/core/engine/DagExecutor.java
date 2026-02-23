package io.loom.core.engine;

import io.loom.core.builder.BuilderContext;
import io.loom.core.builder.BuilderResult;
import io.loom.core.builder.LoomBuilder;
import io.loom.core.exception.BuilderTimeoutException;
import io.loom.core.exception.LoomException;
import io.loom.core.registry.BuilderFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DagExecutor {

    private final BuilderFactory builderFactory;
    private final ExecutorService virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public DagExecutor(BuilderFactory builderFactory) {
        this.builderFactory = builderFactory;
    }

    public Object execute(Dag dag, BuilderContext context) {
        ConcurrentHashMap<Class<? extends LoomBuilder<?>>, CompletableFuture<BuilderResult<?>>> futures = new ConcurrentHashMap<>();

        for (DagNode node : dag.topologicalOrder()) {
            CompletableFuture<BuilderResult<?>> future;

            if (node.dependsOn().isEmpty()) {
                future = CompletableFuture.supplyAsync(() -> executeNode(node, context),
                                                       virtualThreadExecutor);
            } else {
                CompletableFuture<?>[] deps = node.dependsOn().stream().map(dep -> futures.get(dep))
                        .toArray(CompletableFuture[]::new);

                future = CompletableFuture.allOf(deps)
                        .thenApplyAsync(v -> executeNode(node, context), virtualThreadExecutor);
            }

            if (node.required()) {
                future = future.orTimeout(node.timeoutMs(), TimeUnit.MILLISECONDS).exceptionally(ex -> {
                    if (ex instanceof TimeoutException || ex.getCause() instanceof TimeoutException) {
                        throw new BuilderTimeoutException(node.name(), node.timeoutMs());
                    }
                    if (ex instanceof CompletionException ce) {
                        if (ce.getCause() instanceof RuntimeException re) {
                            throw re;
                        }
                        throw new LoomException("Builder '" + node.name() + "' failed", ce.getCause());
                    }
                    if (ex instanceof RuntimeException re) {
                        throw re;
                    }
                    throw new LoomException("Builder '" + node.name() + "' failed", ex);
                });
            } else {
                future = future.completeOnTimeout(BuilderResult.timeout(), node.timeoutMs(), TimeUnit.MILLISECONDS)
                        .exceptionally(ex -> {
                            log.error("[Loom] Optional builder '{}' failed: {}", node.name(),
                                      ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage(), ex);
                            return BuilderResult.failure(ex);
                        });
            }

            futures.put(node.builderClass(), future);
        }

        // Wait for terminal node
        BuilderResult<?> terminalResult = futures.get(dag.getTerminalNode().builderClass()).join();

        if (terminalResult.isFailure()) {
            throw new LoomException("Terminal builder '" + dag.getTerminalNode().name() + "' failed",
                                    terminalResult.error());
        }

        if (terminalResult.timedOut()) {
            throw new BuilderTimeoutException(dag.getTerminalNode().name(), dag.getTerminalNode().timeoutMs());
        }

        return terminalResult.value();
    }

    private BuilderResult<?> executeNode(DagNode node, BuilderContext context) {
        String threadName = Thread.currentThread().toString();
        log.debug("[Loom] Executing node '{}' on virtual thread {}", node.name(), threadName);

        try {
            LoomBuilder<?> builder = builderFactory.createBuilderUntyped(node.builderClass());
            Object result = builder.build(context);

            context.storeResult(node.builderClass(), node.outputType(), result);

            log.debug("[Loom] Node '{}' completed successfully", node.name());
            return BuilderResult.success(result);
        } catch (Exception e) {
            log.error("[Loom] Required builder '{}' failed: {}", node.name(), e.getMessage(), e);
            if (node.required()) {
                throw e;
            }
            return BuilderResult.failure(e);
        }
    }
}
