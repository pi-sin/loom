# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Loom is a Java 21 API Gateway/BFF framework that uses DAG-based scatter-gather patterns with virtual threads. It lets you define data aggregation flows as directed acyclic graphs, executing independent nodes in parallel on virtual threads.

## Build & Test Commands

```bash
# Build all modules
mvn clean install

# Run all tests
mvn test

# Run tests for a specific module
mvn test -pl loom-core
mvn test -pl loom-spring-boot-starter

# Run a single test class
mvn -Dtest=DagExecutorTest test -pl loom-core

# Run example application
cd loom-example && mvn spring-boot:run

# Build and run JMH benchmarks
mvn package -pl loom-benchmark -DskipTests
java -jar loom-benchmark/target/benchmarks.jar -f 1 -wi 3 -i 5 -prof gc
```

## Module Architecture

Five Maven modules with a strict dependency hierarchy:

- **loom-core** — Pure Java, zero Spring dependencies. Contains annotations (`@LoomApi`, `@LoomGraph`, `@Node`, `@LoomProxy`), core interfaces (`LoomBuilder<O>`, `BuilderContext`, `LoomInterceptor`, `ServiceClient`, `ServiceAccessor`, `RouteInvoker`), the DAG engine (`DagCompiler`, `DagValidator`, `DagExecutor`), config records (`ServiceConfig`, `RouteConfig`, `RetryConfig`), and the `JsonCodec`/`DslJsonCodec` JSON abstraction.

- **loom-spring-boot-starter** — Spring Boot auto-configuration layer. Wires core engine into Spring's HTTP dispatch via custom `LoomHandlerMapping` → `LoomHandlerAdapter` → `LoomRequestHandler`. Handles classpath scanning (`LoomAnnotationScanner`), service client management (`RestServiceClient`), and interceptor chains.

- **loom-ui** — Embedded D3.js + dagre-d3 DAG visualization served at `/loom/ui`. Auto-configured as a Spring bean.

- **loom-example** — Reference application with working DAG APIs, passthrough APIs, and interceptors. Configuration in `loom-example/src/main/resources/application.yml`.

- **loom-benchmark** — JMH benchmarks for performance validation. Measures DAG execution throughput, route matching, context creation, JSON codec, and end-to-end pipeline. Run with `java -jar loom-benchmark/target/benchmarks.jar -f 1 -wi 3 -i 5 -prof gc`.

## Key Architectural Concepts

**Request flow:** HTTP request → `LoomHandlerMapping` (route match) → interceptor chain → either DAG execution (`@LoomGraph`) or service proxy (`@LoomProxy`) → response.

**DAG execution:** `DagCompiler` converts `@LoomGraph` annotations into a `Dag` object with pre-indexed arrays (each `DagNode` has an `int index` and `int[] dependencyIndices` computed at compile time). `DagValidator` checks for cycles and detects the terminal node (the builder whose output type matches `@LoomApi.response()`). `DagExecutor` runs nodes on virtual threads via `Executors.newVirtualThreadPerTaskExecutor()`, using `CompletableFuture[]` arrays (not ConcurrentHashMaps) for zero per-request map overhead. Dependent nodes fire as soon as their dependencies resolve.

**Two API modes on the same annotation:**
- `@LoomApi` + `@LoomGraph` = scatter-gather DAG
- `@LoomApi` + `@LoomProxy` = passthrough proxy to external service

**Dependency resolution in builders:** By output type (`ctx.getDependency(Type.class)`) when unique, or by builder class (`ctx.getResultOf(BuilderClass.class)`) when multiple builders produce the same type. Results are stored in a pre-sized `Object[]` array with compile-time-computed index maps (`typeIndexMap`, `builderIndexMap`), eliminating per-request ConcurrentHashMap overhead.

**JSON serialization:** All JSON reading/writing goes through the `JsonCodec` interface (`io.loom.core.codec`), implemented by `DslJsonCodec`. This covers both Loom's direct response writing and Spring `RestClient` service calls (via `DslJsonHttpMessageConverter`).

**Route-based service invocation (Kong-style routes):** Upstream service routes are defined in YAML config under `loom.services.<name>.routes.<route-name>` with `path`, `method`, and optional timeout/retry overrides. Builders use a fluent API: `context.service("svc").route("route-name").get(Type.class)`. Path variables and query params from the incoming request are auto-forwarded; explicit `.pathVar()` / `.queryParam()` overrides take precedence. Route path templates are pre-compiled at startup via `ProxyPathTemplate`. For non-throwing error handling, builders can use `*Response()` methods (e.g. `.getResponse(Type.class)`) which return `ServiceResponse<T>` carrying data + status code + headers + raw body instead of throwing on 4xx/5xx.

**Proxy forwarding:** `@LoomProxy(service = "svc", route = "route-name")` references a named route from config. Path variables are resolved via pre-compiled `ProxyPathTemplate` (zero per-request scanning). Query string is forwarded raw from the servlet request. Headers are forwarded (minus hop-by-hop headers like `Host`/`Content-Length`/`Connection`/`Transfer-Encoding`). Request body is forwarded as raw bytes for POST/PUT/PATCH. Route-level timeout/retry overrides create dedicated `RestServiceClient` instances only when needed; routes sharing service defaults share the same client. Passthrough mode uses `ServiceClient.proxy()` returning `ServiceResponse<byte[]>` so upstream status codes, response headers, and content types are forwarded as-is (not hardcoded to JSON).

**`ServiceResponse<T>`** (`io.loom.core.service`) — Unified response wrapper record carrying `data` (typed), `statusCode`, `headers`, `rawBody`, and `contentType`. Convenience methods: `isSuccessful()`, `isClientError()`, `isServerError()`. Used in two modes:
- **Builder mode:** `RouteInvoker.*Response()` methods (e.g. `getResponse()`, `postResponse()`) call `ServiceClient.exchange()` which returns `ServiceResponse<T>` without throwing on 4xx/5xx, letting builders inspect status and handle errors gracefully.
- **Passthrough mode:** `LoomHandlerAdapter` calls `ServiceClient.proxy()` which returns `ServiceResponse<byte[]>` for raw byte forwarding with proper status/header/content-type propagation.

**Interceptor → builder communication:** Interceptors set attributes via `ctx.setAttribute()`, builders read them via `ctx.getAttribute()`.

## Virtual Thread Conventions

- Embedded server handles each request on a virtual thread (`spring.threads.virtual.enabled: true`)
- Each DAG node runs on its own virtual thread
- Service calls use blocking `RestClient` (virtual thread unmounts during I/O)
- **Never use `synchronized`** — use `ReentrantLock` instead to avoid carrier thread pinning

## Tech Stack

- Java 21 (compiled with `-parameters` flag)
- Spring Boot 3.4+ (version provided by consumer's project; starter is server-agnostic)
- Lombok (used in loom-core)
- dsl-json for JSON serialization (via `JsonCodec` abstraction in `io.loom.core.codec`)
- springdoc-openapi 2.8.6 for Swagger/OpenAPI
- Testing: JUnit 5, AssertJ, Mockito

## Configuration

Loom-specific config lives under `loom:` prefix in application.yml. Key properties:
- `loom.services.<name>.url` — service base URL
- `loom.services.<name>.connect-timeout-ms` — connection timeout (default: 5000)
- `loom.services.<name>.read-timeout-ms` — read timeout (default: 30000)
- `loom.services.<name>.retry.*` — retry config (max-attempts, initial-delay-ms, multiplier, max-delay-ms)
- `loom.services.<name>.routes.<route-name>.path` — upstream path template (e.g. `/products/{id}`)
- `loom.services.<name>.routes.<route-name>.method` — HTTP method (default: GET)
- `loom.services.<name>.routes.<route-name>.connect-timeout-ms` — route-level timeout override (-1 = inherit)
- `loom.services.<name>.routes.<route-name>.read-timeout-ms` — route-level timeout override (-1 = inherit)
- `loom.services.<name>.routes.<route-name>.retry.*` — route-level retry override (null = inherit)
- `loom.max-request-body-size` — max request body in bytes (default: 10485760 / 10MB)
- `loom.swagger.enabled` — enable OpenAPI docs (default: true)
- `loom.ui.enabled` — enable DAG visualization (default: true)
- `loom.basePackages` — custom package scan paths

## Test Locations

Tests exist in `loom-core` and `loom-spring-boot-starter`:
- `loom-core/src/test/.../engine/DagExecutorTest.java` — DAG execution, parallelism, timeouts, optional nodes
- `loom-core/src/test/.../engine/DagCompilerTest.java` — annotation-to-DAG compilation
- `loom-core/src/test/.../engine/DagValidatorTest.java` — cycle detection, terminal node detection
- `loom-core/src/test/.../engine/RetryExecutorTest.java` — exponential backoff with jitter
- `loom-core/src/test/.../codec/DslJsonCodecTest.java` — dsl-json codec round-trip tests
- `loom-core/src/test/.../service/RouteConfigTest.java` — route config timeout detection
- `loom-core/src/test/.../service/ServiceConfigTest.java` — effective timeout/retry resolution
- `loom-spring-boot-starter/src/test/.../web/PathMatcherTest.java` — URL path matching
- `loom-spring-boot-starter/src/test/.../web/LoomHandlerAdapterTest.java` — handler dispatch, passthrough response forwarding, interceptor short-circuit
- `loom-spring-boot-starter/src/test/.../service/RouteInvokerImplTest.java` — fluent route invoker with auto-forwarding and `*Response()` exchange methods
- `loom-spring-boot-starter/src/test/.../service/ServiceClientRegistryTest.java` — route client registration/lookup

## Code Standards

### General Principles

- This is production grade framework meant for public distribution - maintain high quality
- This framework is meant to handle at least 100K+ requests per instance, so performance and scalability are critical. Any design should consider this.
- Follow SOLID principles
- Write clean, maintainable, and well-documented code
- Use meaningful variable and method names
- Keep methods short and focused on a single responsibility
- Avoid code duplication
- Use defensive programming practices
- Write appropriate unit tests
- Document complex algorithms and business logic
- Follow Java coding conventions
- Use proper error handling and logging
- Use proper exception handling and logging
- Use proper resource management
- The framework should be easy to implement and should be extensible
- Update CLAUDE.md and README.md for relevant changes and documentation

### Code Comments

- Only add comments that provide real value beyond what the code already expresses. 
- Avoid obvious comments like `// increment i` or `// return result`. 
- Focus on explaining the "why" behind non-obvious logic, business rules, or complex algorithms.

Examples:


```java
// Good: Explains WHY and provides context
// Use a 30-second timeout because Snowflake's query API can hang indefinitely
// on large result sets. See issue #12345.
Integer connection_timeout = 30;

// Bad: Restates what's obvious from code
// Set connection timeout to 30 seconds
Integer connection_timeout = 30;
```
