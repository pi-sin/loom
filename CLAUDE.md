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
```

## Module Architecture

Four Maven modules with a strict dependency hierarchy:

- **loom-core** — Pure Java, zero Spring dependencies. Contains annotations (`@LoomApi`, `@LoomGraph`, `@Node`, `@LoomProxy`), core interfaces (`LoomBuilder<O>`, `BuilderContext`, `LoomInterceptor`, `UpstreamClient`), and the DAG engine (`DagCompiler`, `DagValidator`, `DagExecutor`).

- **loom-spring-boot-starter** — Spring Boot auto-configuration layer. Wires core engine into Spring's HTTP dispatch via custom `LoomHandlerMapping` → `LoomHandlerAdapter` → `LoomRequestHandler`. Handles classpath scanning (`LoomAnnotationScanner`), upstream client management (`RestClientUpstreamClient`), and interceptor chains.

- **loom-ui** — Embedded D3.js + dagre-d3 DAG visualization served at `/loom/ui`. Auto-configured as a Spring bean.

- **loom-example** — Reference application with working DAG APIs, passthrough APIs, and interceptors. Configuration in `loom-example/src/main/resources/application.yml`.

## Key Architectural Concepts

**Request flow:** HTTP request → `LoomHandlerMapping` (route match) → interceptor chain → either DAG execution (`@LoomGraph`) or upstream proxy (`@LoomProxy`) → response.

**DAG execution:** `DagCompiler` converts `@LoomGraph` annotations into a `Dag` object. `DagValidator` checks for cycles and detects the terminal node (the builder whose output type matches `@LoomApi.response()`). `DagExecutor` runs nodes on virtual threads via `Executors.newVirtualThreadPerTaskExecutor()`, firing dependent nodes as soon as their dependencies resolve.

**Two API modes on the same annotation:**
- `@LoomApi` + `@LoomGraph` = scatter-gather DAG
- `@LoomApi` + `@LoomProxy` = passthrough proxy to upstream service

**Dependency resolution in builders:** By output type (`ctx.getDependency(Type.class)`) when unique, or by builder class (`ctx.getResultOf(BuilderClass.class)`) when multiple builders produce the same type.

**Interceptor → builder communication:** Interceptors set attributes via `ctx.setAttribute()`, builders read them via `ctx.getAttribute()`.

## Virtual Thread Conventions

- Jetty handles each request on a virtual thread (`spring.threads.virtual.enabled: true`)
- Each DAG node runs on its own virtual thread
- Upstream calls use blocking `RestClient` (virtual thread unmounts during I/O)
- **Never use `synchronized`** — use `ReentrantLock` instead to avoid carrier thread pinning

## Tech Stack

- Java 21 (compiled with `-parameters` flag)
- Spring Boot 3.4.3 with Jetty
- Lombok (used in loom-core)
- Jackson for JSON serialization
- springdoc-openapi 2.8.6 for Swagger/OpenAPI
- Testing: JUnit 5, AssertJ, Mockito

## Configuration

Loom-specific config lives under `loom:` prefix in application.yml. Key properties:
- `loom.upstreams.<name>.base-url` — upstream service URL
- `loom.upstreams.<name>.retry.*` — retry config (max-attempts, initial-delay-ms, multiplier, max-delay-ms)
- `loom.swagger.enabled` — enable OpenAPI docs (default: true)
- `loom.ui.enabled` — enable DAG visualization (default: true)
- `loom.basePackages` — custom package scan paths

## Test Locations

Tests exist in `loom-core` and `loom-spring-boot-starter`:
- `loom-core/src/test/.../engine/DagExecutorTest.java` — DAG execution, parallelism, timeouts, optional nodes
- `loom-core/src/test/.../engine/DagCompilerTest.java` — annotation-to-DAG compilation
- `loom-core/src/test/.../engine/DagValidatorTest.java` — cycle detection, terminal node detection
- `loom-core/src/test/.../engine/RetryExecutorTest.java` — exponential backoff with jitter
- `loom-spring-boot-starter/src/test/.../web/PathMatcherTest.java` — URL path matching


## Core Requirements

- The architecture should be simple and easy to understand
- The architecture should be scalable to high concurrency and throughput (1M req/sec) and maintainable
- The architecture should be easy to test
- The architecture should be easy to extend
- The architecture should be easy to debug
- The architecture should be easy to document
- The architecture should be easy to deploy
- The architecture should be easy to monitor
- The architecture should be easy to secure
- The architecture should be easy to scale
- The architecture should be easy to maintain
- The architecture should be easy to refactor
- The architecture should be easy to understand
- The architecture should be easy to learn
- The architecture should be easy to teach
- The architecture should be easy to follow
- The architecture should be easy to remember
- The architecture should be easy to implement
- The architecture should be easy to deploy
- The architecture should be easy to maintain
- The architecture should be easy to extend
- The architecture should be easy to test
- The architecture should be easy to debug
- The architecture should be easy to document
- The architecture should be easy to monitor
- The architecture should be easy to secure
- The architecture should be easy to scale
- The architecture should be easy to maintain
- The architecture should be easy to refactor
- The architecture should be easy to understand
- The architecture should be easy to learn
- The architecture should be easy to teach
- The architecture should be easy to follow
- The architecture should be easy to remember
- The architecture should be easy to implement