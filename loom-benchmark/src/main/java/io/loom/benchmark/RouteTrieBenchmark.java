package io.loom.benchmark;

import io.loom.core.model.ApiDefinition;
import io.loom.starter.web.RouteTrie;

import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class RouteTrieBenchmark {

    private RouteTrie trie;

    private static ApiDefinition api(String method, String path) {
        return new ApiDefinition(method, path, null, null, null, null,
                null, null, null, null, null, null, null, null, null);
    }

    @Setup
    public void setup() {
        trie = new RouteTrie();

        // 50 routes: mix of static and parameterized
        String[] resources = {"users", "products", "orders", "reviews", "payments",
                "invoices", "shipments", "categories", "tags", "comments"};
        String[] actions = {"profile", "settings", "history", "details", "summary"};

        for (String resource : resources) {
            trie.insert(api("GET", "/api/v1/" + resource));
            trie.insert(api("POST", "/api/v1/" + resource));
            trie.insert(api("GET", "/api/v1/" + resource + "/{id}"));
        }

        for (int i = 0; i < resources.length; i++) {
            String action = actions[i % actions.length];
            trie.insert(api("GET", "/api/v1/" + resources[i] + "/{id}/" + action));
        }

        for (int i = 0; i < 10; i++) {
            trie.insert(api("GET", "/api/v2/resource" + i + "/{parentId}/child/{childId}"));
        }
    }

    @Benchmark
    public RouteTrie.RouteMatch findStaticRoute() {
        return trie.find("GET", "/api/v1/users");
    }

    @Benchmark
    public RouteTrie.RouteMatch findSingleParam() {
        return trie.find("GET", "/api/v1/products/42");
    }

    @Benchmark
    public RouteTrie.RouteMatch findDoubleParam() {
        return trie.find("GET", "/api/v2/resource5/100/child/200");
    }

    @Benchmark
    public RouteTrie.RouteMatch findDeepParamPath() {
        return trie.find("GET", "/api/v1/users/42/profile");
    }

    @Benchmark
    public RouteTrie.RouteMatch findMiss() {
        return trie.find("GET", "/api/v1/nonexistent/42");
    }
}
