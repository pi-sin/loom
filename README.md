# Loom Framework

**Java 21 API Gateway/BFF framework with DAG-based scatter-gather, powered by virtual threads.**

Loom makes it trivial to build API gateways and Backend-for-Frontend (BFF) services that aggregate
data from multiple upstream services. Define your data flow as a directed acyclic graph (DAG), and
Loom executes it with maximum parallelism using virtual threads.

## Features

- **Declarative DAG composition** — Define scatter-gather flows with annotations
- **Zero-code passthrough** — Proxy upstream APIs with YAML config alone
- **Virtual thread execution** — Every request, every DAG node, every upstream call runs on a
  virtual thread
- **Compile-time type safety** — Dependencies reference builder classes, not strings
- **Auto terminal detection** — The builder whose output matches the response type is the terminal
  node
- **Built-in retry with backoff** — Exponential backoff with jitter on virtual threads (sleep is
  free)
- **Interceptor chains** — Request/response interceptors with attribute passing to builders
- **Embedded DAG visualization** — Dark-themed UI at `/loom/ui` powered by D3.js + dagre-d3
- **Built-in Swagger/OpenAPI** — Auto-generated API docs from `@LoomApi` annotations at
  `/swagger-ui.html`

**Use Loom if your use case looks like this:**

```
                              GET /api/products/{id}
                                       |
                                  Loom Gateway
                              /        |        \
                             /         |         \
                  Product SVC    Pricing SVC    Review SVC        <-- Level 1 (parallel)
                 (ProductInfo)  (PricingInfo)  (ReviewList)
                      |  \         / |               |
                      |   +---+---+  |               |
                      |       |      +--------+      |
                      |       v               |      |
                      |  Recommendations SVC  |      |            <-- Level 2
                      |  (RecommendationList) |      |
                      |       |      |        |      |
                      v       v      v        v      v
                           AssembleProductBuilder                  <-- Level 3 (all 4)
                           (ProductDetailResponse)
```

Three services fan out in parallel, a fourth waits for two of them, and a terminal node assembles
everything.

## Quick Start

### 1. Add Dependencies

```xml

<dependency>
  <groupId>io.loom</groupId>
  <artifactId>loom-spring-boot-starter</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
<dependency>
<groupId>io.loom</groupId>
<artifactId>loom-ui</artifactId>
<version>0.1.0-SNAPSHOT</version>
</dependency>
```

### 2. Define a DAG API

```java

@LoomApi(method = "GET", path = "/api/products/{id}", response = ProductDetailResponse.class)
@LoomGraph({@Node(builder = FetchProductBuilder.class, timeoutMs = 3000),
        @Node(builder = FetchPricingBuilder.class, dependsOn = FetchProductBuilder.class),
        @Node(builder = FetchReviewsBuilder.class, required = false, timeoutMs = 2000), @Node(
        builder = FetchRecommendationsBuilder.class,
        required = false,
        timeoutMs = 2000,
        dependsOn = {FetchProductBuilder.class, FetchPricingBuilder.class}),
        @Node(builder = AssembleProductBuilder.class,
              dependsOn = {FetchProductBuilder.class, FetchPricingBuilder.class,
                      FetchReviewsBuilder.class, FetchRecommendationsBuilder.class})})
public class ProductDetailApi {}
```

> **Note:** `@LoomApi` and `@LoomPassthrough` classes are auto-registered as Spring beans by the
> starter — no `@Component` needed.

### 3. Implement Builders

```java

@Component
public class FetchProductBuilder implements LoomBuilder<ProductInfo> {
    public ProductInfo build(BuilderContext ctx) {
        String id = ctx.getPathVariable("id");
        return ctx.upstream("product-service").get("/products/" + id, ProductInfo.class);
    }
}

@Component
public class AssembleProductBuilder implements LoomBuilder<ProductDetailResponse> {
    public ProductDetailResponse build(BuilderContext ctx) {
        ProductInfo info = ctx.getDependency(ProductInfo.class);
        PricingInfo pricing = ctx.getDependency(PricingInfo.class);
        Optional<List<Review>> reviews = ctx.getOptionalResultOf(FetchReviewsBuilder.class);
        return new ProductDetailResponse(info, pricing, reviews.orElse(List.of()));
    }
}
```

### 4. Configure Upstreams

```yaml
spring:
  threads:
    virtual:
      enabled: true

loom:
  upstreams:
    product-service:
      base-url: http://localhost:8081
      retry:
        max-attempts: 3
        initial-delay-ms: 100
```

### 5. Add a Passthrough (Zero Code)

```yaml
# loom.yml
passthrough:
  - path: /api/health
    method: GET
    upstream: health-service
    upstream-path: /internal/health
```

## Architecture

```
HTTP Request (virtual thread via Jetty)
  |
  v
LoomHandlerMapping (path template match)
  |
  v
Interceptor chain (global + per-route)
  |
  +--[Builder mode]----> DagExecutor
  |                        |
  |                        +-- Node A (virtual thread) --+
  |                        +-- Node B (virtual thread) --+---> Terminal Node
  |                        +-- Node C (virtual thread) --+     (auto-detected)
  |                        |
  |                        v
  |                      Response DTO
  |
  +--[Passthrough]-----> RestClient -> Upstream -> Response
  |
  v
Interceptor chain (response phase)
  |
  v
HTTP Response
```

## Execution Flow

```
  FetchProduct ─────────────────────────────┐
  FetchPricing (depends: FetchProduct) ─────┤
  FetchReviews (optional, parallel) ────────┼──> AssembleProduct (terminal)
  FetchRecommendations (depends: Product,   │         |
                        Pricing; optional) ─┘         v
                                              ProductDetailResponse
```

All independent nodes execute in parallel on virtual threads. Dependent nodes fire the instant their
dependencies resolve.

## API Reference

### Annotations

| Annotation         | Target | Purpose                                                                     |
|--------------------|--------|-----------------------------------------------------------------------------|
| `@LoomApi`         | Class  | Route definition (method, path, request/response types, interceptors, docs) |
| `@LoomGraph`       | Class  | DAG definition, placed on same class as `@LoomApi`                          |
| `@Node`            | Nested | Individual DAG node (builder class, dependencies, required, timeout)        |
| `@LoomPassthrough` | Class  | Annotation-based passthrough (alternative to YAML)                          |
| `@LoomQueryParam`  | Nested | Declares a query parameter (name, type, required, default, description)     |
| `@LoomHeaderParam` | Nested | Declares a required/documented header (name, required, description)         |

### Core Interfaces

| Interface         | Purpose                                                                                               |
|-------------------|-------------------------------------------------------------------------------------------------------|
| `LoomBuilder<O>`  | DAG node implementation. `O build(BuilderContext ctx)`                                                |
| `BuilderContext`  | Shared context for all builders in a request                                                          |
| `LoomInterceptor` | Request/response processing. `void handle(LoomHttpContext, InterceptorChain)` + `default int order()` |
| `UpstreamClient`  | HTTP client for upstream calls (get/post/put/delete/patch)                                            |

### BuilderContext Methods

| Method                              | Description                                         |
|-------------------------------------|-----------------------------------------------------|
| `getPathVariable(name)`             | Extract path variable                               |
| `getQueryParam(name)`               | Get query parameter                                 |
| `getHeader(name)`                   | Get request header                                  |
| `getRequestBody(type)`              | Deserialize request body                            |
| `getDependency(outputType)`         | Get dependency by output type (throws if missing)   |
| `getResultOf(builderClass)`         | Get dependency by builder class (throws if missing) |
| `getOptionalDependency(outputType)` | Get optional dependency by output type              |
| `getOptionalResultOf(builderClass)` | Get optional dependency by builder class            |
| `upstream(name)`                    | Get upstream HTTP client                            |
| `getAttribute(key, type)`           | Get attribute set by interceptor                    |
| `getRequestId()`                    | Auto-generated correlation ID                       |

### Configuration

```yaml
loom:
  config-file: loom.yml            # YAML config file path
  upstreams:
    service-name:
      base-url: http://host:port
      connect-timeout-ms: 5000
      read-timeout-ms: 30000
      retry:
        max-attempts: 3
        initial-delay-ms: 100
        multiplier: 2.0
        max-delay-ms: 5000
  ui:
    enabled: true                  # Enable DAG visualization at /loom/ui
```

## Swagger / OpenAPI

Loom auto-generates an OpenAPI spec from your `@LoomApi` and `@LoomPassthrough` annotations. Add the
springdoc dependency to your app:

```xml

<dependency>
  <groupId>org.springdoc</groupId>
  <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
  <version>2.8.6</version>
</dependency>
```

Then visit:

- **Swagger UI:** `http://localhost:8080/swagger-ui.html`
- **OpenAPI JSON:** `http://localhost:8080/v3/api-docs`

### API Metadata Annotations

Declare query parameters, headers, and documentation metadata directly on your API class:

```java
@LoomApi(method = "GET",
         path = "/api/products/{id}",
         response = ProductDetailResponse.class,
         summary = "Get product details",
         description = "Fetches product info, pricing, reviews and recommendations in parallel",
         tags = {"Products"},
         queryParams = {@LoomQueryParam(name = "currency", description = "Price currency"),
                 @LoomQueryParam(name = "fields",
                                 description = "Comma-separated fields to include")},
         headers = {@LoomHeaderParam(name = "X-API-Key",
                                     required = true,
                                     description = "API authentication key")},
         interceptors = {ApiKeyInterceptor.class})
```

Passthrough routes also support documentation:

```java
@LoomPassthrough(method = "GET",
                 path = "/api/health",
                 upstream = "health-service",
                 upstreamPath = "/internal/health",
                 summary = "Health check",
                 tags = {"Infrastructure"})
```

### Configuration

```yaml
loom:
  swagger:
    enabled: true   # default: false, must opt in to enable
```

## Upstream API Cookbook

### GET with Path Params

```java

@Component
public class FetchProductBuilder implements LoomBuilder<ProductInfo> {
    public ProductInfo build(BuilderContext ctx) {
        String id = ctx.getPathVariable("id");
        return ctx.upstream("product-service").get("/products/" + id, ProductInfo.class);
    }
}
```

### GET with Query Params

```java

@Component
public class SearchProductsBuilder implements LoomBuilder<ProductList> {
    public ProductList build(BuilderContext ctx) {
        String category = ctx.getQueryParam("category");
        String page = ctx.getQueryParam("page");
        String sort = ctx.getQueryParam("sort");

        String path =
                "/products?category=" + category + "&page=" + (page != null ? page : "1") + "&sort="
                        + (sort != null ? sort : "relevance");

        return ctx.upstream("product-service").get(path, ProductList.class);
    }
}
```

For `GET /api/products?category=electronics&page=2&sort=price`, the builder receives all query
params via `ctx.getQueryParam(name)`. For multi-valued params, use `ctx.getQueryParams()` which
returns `Map<String, List<String>>`.

### POST with Request Body

```java
// API definition — note the `request` type for incoming body
@LoomApi(method = "POST",
         path = "/api/orders",
         request = CreateOrderRequest.class,
         response = OrderResponse.class)
@LoomGraph({@Node(builder = ValidateOrderBuilder.class),
        @Node(builder = CreateOrderBuilder.class, dependsOn = ValidateOrderBuilder.class),
        @Node(builder = SendConfirmationBuilder.class, dependsOn = CreateOrderBuilder.class)})
public class CreateOrderApi {}

// DTOs
public record CreateOrderRequest(String productId, int quantity, String shippingAddress) {}

public record OrderResponse(String orderId, String status, String estimatedDelivery) {}

// Builder — reads the incoming request body, POSTs to upstream
@Component
public class CreateOrderBuilder implements LoomBuilder<OrderResponse> {
    public OrderResponse build(BuilderContext ctx) {
        CreateOrderRequest request = ctx.getRequestBody(CreateOrderRequest.class);

        return ctx.upstream("order-service").post("/internal/orders", request, OrderResponse.class);
    }
}
```

### PUT with Custom Headers

```java

@Component
public class UpdateProfileBuilder implements LoomBuilder<UserProfile> {
    public UserProfile build(BuilderContext ctx) {
        String userId = ctx.getPathVariable("userId");
        UpdateProfileRequest body = ctx.getRequestBody(UpdateProfileRequest.class);

        // Forward auth header from original request + add custom headers
        Map<String, String> headers = Map.of("Authorization", ctx.getHeader("Authorization"),
                                             "X-Request-ID", ctx.getRequestId(), "Content-Type",
                                             "application/json");

        return ctx.upstream("user-service")
                .put("/users/" + userId, body, UserProfile.class, headers);
    }
}
```

### DELETE

```java

@Component
public class DeleteCartItemBuilder implements LoomBuilder<Void> {
    public Void build(BuilderContext ctx) {
        String cartId = ctx.getPathVariable("cartId");
        String itemId = ctx.getPathVariable("itemId");

        return ctx.upstream("cart-service")
                .delete("/carts/" + cartId + "/items/" + itemId, Void.class);
    }
}
```

### PATCH with Partial Update

```java

@Component
public class PatchOrderBuilder implements LoomBuilder<OrderResponse> {
    public OrderResponse build(BuilderContext ctx) {
        String orderId = ctx.getPathVariable("orderId");
        Map<String, Object> patch = ctx.getRequestBody(Map.class);

        return ctx.upstream("order-service")
                .patch("/orders/" + orderId, patch, OrderResponse.class);
    }
}
```

### Reading Headers Set by Interceptor

Interceptors can authenticate and set attributes that builders read:

```java
// Interceptor sets authenticated user
@Component
public class AuthInterceptor implements LoomInterceptor {
    public void handle(LoomHttpContext ctx, InterceptorChain chain) {
        String token = ctx.getHeader("Authorization");
        User user = authService.validate(token);
        ctx.setAttribute("authenticatedUser", user);
        chain.next(ctx);
    }
}

// Builder reads interceptor attribute + forwards auth downstream
@Component
public class FetchUserDataBuilder implements LoomBuilder<UserData> {
    public UserData build(BuilderContext ctx) {
        User user = ctx.getAttribute("authenticatedUser", User.class);

        Map<String, String> headers = Map.of("X-User-ID", user.id(), "X-Correlation-ID",
                                             ctx.getRequestId());

        return ctx.upstream("user-service")
                .get("/users/" + user.id() + "/data", UserData.class, headers);
    }
}
```

### Resolving Dependencies

Use `getDependency` / `getOptionalDependency` to look up a builder result by its **output type**.
Use the `*From` variants to look up by **builder class** — needed when multiple builders produce the
same type.

```java

@Component
public class AssembleOrderBuilder implements LoomBuilder<OrderSummary> {
    public OrderSummary build(BuilderContext ctx) {
        // By output type — works when only one builder produces this type
        OrderInfo order = ctx.getDependency(OrderInfo.class);

        // By builder class — needed when two builders produce the same type (e.g. ShippingEstimate)
        ShippingEstimate domestic = ctx.getResultOf(DomesticShippingBuilder.class);
        ShippingEstimate international = ctx.getResultOf(InternationalShippingBuilder.class);

        // Optional by output type — returns Optional.empty() if the builder was non-required and failed
        Optional<LoyaltyPoints> loyalty = ctx.getOptionalDependency(LoyaltyPoints.class);

        // Optional by builder class — same, but disambiguates by builder
        Optional<ShippingEstimate> express = ctx.getOptionalResultOf(ExpressShippingBuilder.class);

        return new OrderSummary(order, domestic, international, loyalty.orElse(null),
                                express.orElse(null));
    }
}
```

### Accessing All Request Data

```java

@Component
public class DebugBuilder implements LoomBuilder<Map<String, Object>> {
    public Map<String, Object> build(BuilderContext ctx) {
        // All path variables: {id: "42", slug: "widget"}
        Map<String, String> pathVars = ctx.getPathVariables();

        // All query params (multi-valued): {sort: ["price", "name"], page: ["1"]}
        Map<String, List<String>> queryParams = ctx.getQueryParams();

        // All headers (multi-valued): {accept: ["application/json"], ...}
        Map<String, List<String>> headers = ctx.getHeaders();

        // Raw request body bytes (useful for binary or non-JSON)
        byte[] rawBody = ctx.getRawRequestBody();

        // HTTP method and path
        String method = ctx.getHttpMethod();  // "GET", "POST", etc.
        String path = ctx.getRequestPath();   // "/api/products/42"

        // Auto-generated request correlation ID
        String requestId = ctx.getRequestId();

        return Map.of("method", method, "path", path, "pathVars", pathVars);
    }
}
```

### UpstreamClient Method Reference

| Method   | Signature                                  | Use Case                 |
|----------|--------------------------------------------|--------------------------|
| `get`    | `get(path, responseType)`                  | Simple GET               |
| `get`    | `get(path, responseType, headers)`         | GET with custom headers  |
| `post`   | `post(path, body, responseType)`           | POST with JSON body      |
| `post`   | `post(path, body, responseType, headers)`  | POST with body + headers |
| `put`    | `put(path, body, responseType)`            | Full resource update     |
| `put`    | `put(path, body, responseType, headers)`   | PUT with headers         |
| `delete` | `delete(path, responseType)`               | Delete resource          |
| `delete` | `delete(path, responseType, headers)`      | Delete with auth headers |
| `patch`  | `patch(path, body, responseType)`          | Partial update           |
| `patch`  | `patch(path, body, responseType, headers)` | Partial update + headers |

All upstream calls are **blocking on virtual threads** — the virtual thread unmounts from the
carrier thread during I/O wait, so blocking is as efficient as async with much simpler code. Retry
with exponential backoff is automatic based on upstream configuration.

## Virtual Threads

Loom uses virtual threads at every layer:

1. **HTTP handling** — Jetty spawns virtual threads for each request (
   `spring.threads.virtual.enabled: true`)
2. **DAG execution** — Each builder node runs on its own virtual thread via
   `Executors.newVirtualThreadPerTaskExecutor()`
3. **Upstream calls** — Blocking `RestClient` calls unmount from carrier threads during I/O
4. **Retry backoff** — `Thread.sleep()` on virtual threads has zero platform thread cost
5. **Interceptors** — Entire chain runs on the request's virtual thread; blocking calls are safe

**Anti-pinning rules:** The framework uses `ReentrantLock` instead of `synchronized` everywhere. If
you need locking in your builders, use `ReentrantLock`.

## Module Structure

```
loom/
├── loom-core/                     # Pure Java — zero Spring deps
├── loom-spring-boot-starter/      # Spring Boot auto-configuration
├── loom-ui/                       # Embedded DAG visualization
└── loom-example/                  # Working demo app
```

## Building

```bash
mvn clean install
```

## Running the Example

```bash
cd loom-example
mvn spring-boot:run
```

Then visit:

- `GET http://localhost:8080/api/products/42` (with header `X-API-Key: demo-api-key-12345`)
- `GET http://localhost:8080/api/users/123/dashboard`
- `GET http://localhost:8080/loom/ui` — DAG visualization

## Requirements

- Java 21+
- Spring Boot 3.4+
- Jetty (included via starter)

## License

Apache License 2.0
