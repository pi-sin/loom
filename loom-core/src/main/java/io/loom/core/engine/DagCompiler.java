package io.loom.core.engine;

import io.loom.core.annotation.LoomApi;
import io.loom.core.annotation.LoomGraph;
import io.loom.core.annotation.Node;
import io.loom.core.builder.LoomBuilder;
import io.loom.core.exception.LoomException;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

@Slf4j
public class DagCompiler {

    private final DagValidator validator = new DagValidator();

    public Dag compile(Class<?> apiClass) {
        LoomApi api = apiClass.getAnnotation(LoomApi.class);
        LoomGraph graph = apiClass.getAnnotation(LoomGraph.class);

        if (api == null) {
            throw new LoomException("Class " + apiClass.getSimpleName() + " is missing @LoomApi annotation");
        }
        if (graph == null) {
            throw new LoomException("Class " + apiClass.getSimpleName() + " is missing @LoomGraph annotation");
        }

        return compile(graph.value(), api.response());
    }

    @SuppressWarnings("unchecked")
    public Dag compile(Node[] nodeAnnotations, Class<?> responseType) {
        Map<Class<? extends LoomBuilder<?>>, DagNode> nodes = new LinkedHashMap<>();

        for (Node nodeAnn : nodeAnnotations) {
            Class<? extends LoomBuilder<?>> builderClass = nodeAnn.builder();
            Class<?> outputType = resolveOutputType(builderClass);

            Set<Class<? extends LoomBuilder<?>>> deps = new LinkedHashSet<>(Arrays.asList(nodeAnn.dependsOn()));

            DagNode dagNode = new DagNode(
                    builderClass,
                    deps,
                    nodeAnn.required(),
                    nodeAnn.timeoutMs(),
                    outputType
            );

            if (nodes.containsKey(builderClass)) {
                throw new LoomException("Duplicate builder class in graph: " + builderClass.getSimpleName());
            }

            nodes.put(builderClass, dagNode);
        }

        DagValidator.ValidationResult result = validator.validate(nodes, responseType);

        log.info("[Loom] Compiled DAG with {} nodes, terminal='{}'",
                nodes.size(), result.terminalNode().name());

        return new Dag(nodes, result.topologicalOrder(), result.terminalNode());
    }

    public static Class<?> resolveOutputType(Class<? extends LoomBuilder<?>> builderClass) {
        Type[] interfaces = builderClass.getGenericInterfaces();
        for (Type iface : interfaces) {
            if (iface instanceof ParameterizedType pt) {
                if (pt.getRawType().equals(LoomBuilder.class)) {
                    Type typeArg = pt.getActualTypeArguments()[0];
                    if (typeArg instanceof Class<?> clazz) {
                        return clazz;
                    }
                    if (typeArg instanceof ParameterizedType paramType) {
                        return (Class<?>) paramType.getRawType();
                    }
                }
            }
        }

        // Check intermediate interfaces that extend LoomBuilder
        for (Type iface : interfaces) {
            Class<?> rawIface = (iface instanceof ParameterizedType pt)
                    ? (Class<?>) pt.getRawType() : (iface instanceof Class<?> c ? c : null);
            if (rawIface != null && LoomBuilder.class.isAssignableFrom(rawIface)
                    && rawIface != LoomBuilder.class) {
                @SuppressWarnings("unchecked")
                Class<? extends LoomBuilder<?>> ifaceBuilder = (Class<? extends LoomBuilder<?>>) rawIface;
                return resolveOutputType(ifaceBuilder);
            }
        }

        // Check superclass chain
        Class<?> superClass = builderClass.getSuperclass();
        if (superClass != null && LoomBuilder.class.isAssignableFrom(superClass)) {
            @SuppressWarnings("unchecked")
            Class<? extends LoomBuilder<?>> superBuilder = (Class<? extends LoomBuilder<?>>) superClass;
            return resolveOutputType(superBuilder);
        }

        throw new LoomException("Cannot resolve output type for builder: " + builderClass.getSimpleName()
                + ". Ensure it directly implements LoomBuilder<T> with a concrete type parameter.");
    }
}
