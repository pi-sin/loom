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
        List<DagNode> topoOrder = result.topologicalOrder();

        // Assign sequential indices based on topological order
        Map<Class<? extends LoomBuilder<?>>, Integer> builderToIndex = new HashMap<>();
        List<DagNode> indexedOrder = new ArrayList<>(topoOrder.size());

        for (int i = 0; i < topoOrder.size(); i++) {
            DagNode original = topoOrder.get(i);
            builderToIndex.put(original.builderClass(), i);

            // Compute dependency indices
            int[] depIndices = new int[original.dependsOn().size()];
            int di = 0;
            for (Class<? extends LoomBuilder<?>> dep : original.dependsOn()) {
                Integer depIdx = builderToIndex.get(dep);
                if (depIdx == null) {
                    throw new LoomException("Dependency '" + dep.getSimpleName()
                            + "' not found in topological order for node '" + original.name() + "'");
                }
                depIndices[di++] = depIdx;
            }

            DagNode indexed = new DagNode(
                    original.builderClass(),
                    original.dependsOn(),
                    original.required(),
                    original.timeoutMs(),
                    original.outputType(),
                    i,
                    depIndices
            );
            indexedOrder.add(indexed);
        }

        // Rebuild nodes map with indexed nodes
        Map<Class<? extends LoomBuilder<?>>, DagNode> indexedNodes = new LinkedHashMap<>();
        for (DagNode node : indexedOrder) {
            indexedNodes.put(node.builderClass(), node);
        }

        // Build type→index and builder→index maps
        Map<Class<?>, Integer> typeIndexMap = new HashMap<>();
        Map<Class<? extends LoomBuilder<?>>, Integer> builderIndexMap = new HashMap<>();
        for (DagNode node : indexedOrder) {
            typeIndexMap.put(node.outputType(), node.index());
            builderIndexMap.put(node.builderClass(), node.index());
        }

        DagNode terminal = indexedNodes.get(result.terminalNode().builderClass());

        log.info("[Loom] Compiled DAG with {} nodes, terminal='{}'",
                indexedNodes.size(), terminal.name());

        return new Dag(indexedNodes, indexedOrder, terminal, typeIndexMap, builderIndexMap);
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
