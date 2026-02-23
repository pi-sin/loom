package io.loom.core.engine;

import io.loom.core.builder.BuilderContext;
import io.loom.core.builder.BuilderResult;
import io.loom.core.builder.LoomBuilder;
import io.loom.core.exception.BuilderTimeoutException;
import io.loom.core.exception.LoomException;
import io.loom.core.registry.BuilderFactory;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
public class DagExecutor {

    private final BuilderFactory builderFactory;

    public DagExecutor(BuilderFactory builderFactory) {
        this.builderFactory = builderFactory;
    }

    public Object execute(Dag dag, BuilderContext context) {
        ExecutorService vtExec = Executors.newVirtualThreadPerTaskExecutor();
        ConcurrentHashMap<Class<? extends LoomBuilder<?>>, CompletableFuture<BuilderResult<?>>> futures =
                new ConcurrentHashMap<>();
        ConcurrentHashMap<Class<? extends LoomBuilder<?>>, Object> results = new ConcurrentHashMap<>();
        ReentrantLock resultsLock = new ReentrantLock();

        try {
            for (DagNode node : dag.topologicalOrder()) {
                CompletableFuture<BuilderResult<?>> future;

                if (node.dependsOn().isEmpty()) {
                    future = CompletableFuture.supplyAsync(
                            () -> executeNode(node, context, results, resultsLock), vtExec);
                } else {
                    CompletableFuture<?>[] deps = node.dependsOn().stream()
                            .map(dep -> futures.get(dep))
                            .toArray(CompletableFuture[]::new);

                    future = CompletableFuture.allOf(deps)
                            .thenApplyAsync(v -> executeNode(node, context, results, resultsLock), vtExec);
                }

                if (node.required()) {
                    future = future.orTimeout(node.timeoutMs(), TimeUnit.MILLISECONDS)
                            .exceptionally(ex -> {
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
                                log.warn("[Loom] Optional builder '{}' failed: {}", node.name(),
                                        ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage());
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
                throw new BuilderTimeoutException(dag.getTerminalNode().name(),
                        dag.getTerminalNode().timeoutMs());
            }

            return terminalResult.value();
        } finally {
            vtExec.shutdown();
        }
    }

    private BuilderResult<?> executeNode(DagNode node, BuilderContext context,
                                         ConcurrentHashMap<Class<? extends LoomBuilder<?>>, Object> results,
                                         ReentrantLock resultsLock) {
        String threadName = Thread.currentThread().toString();
        log.debug("[Loom] Executing node '{}' on virtual thread {}", node.name(), threadName);

        try {
            LoomBuilder<?> builder = builderFactory.createBuilderUntyped(node.builderClass());
            Object result = builder.build(context);

            resultsLock.lock();
            try {
                results.put(node.builderClass(), result);
            } finally {
                resultsLock.unlock();
            }

            log.debug("[Loom] Node '{}' completed successfully", node.name());
            return BuilderResult.success(result);
        } catch (Exception e) {
            log.error("[Loom] Required builder '{}' failed: {}", node.name(), e.getMessage());
            if (node.required()) {
                throw e;
            }
            return BuilderResult.failure(e);
        }
    }
}
