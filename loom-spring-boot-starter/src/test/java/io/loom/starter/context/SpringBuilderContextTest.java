package io.loom.starter.context;

import io.loom.core.builder.LoomBuilder;
import io.loom.core.builder.BuilderContext;
import io.loom.core.codec.JsonCodec;
import io.loom.core.exception.LoomDependencyResolutionException;
import io.loom.starter.service.ServiceClientRegistry;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SpringBuilderContextTest {

    // ── Stub builders and types ──

    record Alpha(String value) {}
    record Beta(int value) {}
    record Gamma(String combined) {}

    static class AlphaBuilder implements LoomBuilder<Alpha> {
        public Alpha build(BuilderContext ctx) { return new Alpha("a"); }
    }
    static class BetaBuilder implements LoomBuilder<Beta> {
        public Beta build(BuilderContext ctx) { return new Beta(42); }
    }
    static class GammaBuilder implements LoomBuilder<Gamma> {
        public Gamma build(BuilderContext ctx) { return new Gamma("g"); }
    }

    // ── Helper ──

    @SuppressWarnings("unchecked")
    private SpringBuilderContext createContext() {
        JsonCodec codec = mock(JsonCodec.class);
        ServiceClientRegistry registry = mock(ServiceClientRegistry.class);
        return new SpringBuilderContext(
                "GET", "/test",
                Map.of(), Map.of(), Map.of(),
                null, codec, registry, null);
    }

    private void initThreeNodeStorage(SpringBuilderContext ctx) {
        Map<Class<?>, Integer> typeIndexMap = new HashMap<>();
        typeIndexMap.put(Alpha.class, 0);
        typeIndexMap.put(Beta.class, 1);
        typeIndexMap.put(Gamma.class, 2);

        Map<Class<? extends LoomBuilder<?>>, Integer> builderIndexMap = new HashMap<>();
        builderIndexMap.put(AlphaBuilder.class, 0);
        builderIndexMap.put(BetaBuilder.class, 1);
        builderIndexMap.put(GammaBuilder.class, 2);

        ctx.initResultStorage(3, typeIndexMap, builderIndexMap);
    }

    // ── Tests ──

    @Test
    void initResultStorage_createsCorrectSizedArray() {
        SpringBuilderContext ctx = createContext();
        initThreeNodeStorage(ctx);

        // Unpopulated slots should return Optional.empty()
        assertThat(ctx.getOptionalDependency(Alpha.class)).isEmpty();
        assertThat(ctx.getOptionalDependency(Beta.class)).isEmpty();
        assertThat(ctx.getOptionalDependency(Gamma.class)).isEmpty();
    }

    @Test
    void storeAndGetDependency_roundTrips() {
        SpringBuilderContext ctx = createContext();
        initThreeNodeStorage(ctx);

        Alpha alpha = new Alpha("hello");
        ctx.storeResult(AlphaBuilder.class, Alpha.class, alpha);

        Alpha retrieved = ctx.getDependency(Alpha.class);
        assertThat(retrieved).isSameAs(alpha);
        assertThat(retrieved.value()).isEqualTo("hello");
    }

    @Test
    void storeAndGetResultOf_roundTrips() {
        SpringBuilderContext ctx = createContext();
        initThreeNodeStorage(ctx);

        Beta beta = new Beta(99);
        ctx.storeResult(BetaBuilder.class, Beta.class, beta);

        Beta retrieved = ctx.getResultOf(BetaBuilder.class);
        assertThat(retrieved).isSameAs(beta);
        assertThat(retrieved.value()).isEqualTo(99);
    }

    @Test
    void storeNull_returnsNullViaSentinel() {
        SpringBuilderContext ctx = createContext();
        initThreeNodeStorage(ctx);

        ctx.storeResult(AlphaBuilder.class, Alpha.class, null);

        // getDependency should return null (unwrapped from sentinel)
        Alpha result = ctx.getDependency(Alpha.class);
        assertThat(result).isNull();

        // getOptionalDependency should return Optional with null inside → Optional.empty() via ofNullable
        Optional<Alpha> optional = ctx.getOptionalDependency(Alpha.class);
        assertThat(optional).isEmpty();
    }

    @Test
    void getOptionalDependency_unregisteredType_returnsEmpty() {
        SpringBuilderContext ctx = createContext();
        initThreeNodeStorage(ctx);

        // String is not in the DAG's typeIndexMap
        Optional<String> result = ctx.getOptionalDependency(String.class);
        assertThat(result).isEmpty();
    }

    @Test
    void getOptionalResultOf_unregisteredBuilder_returnsEmpty() {
        SpringBuilderContext ctx = createContext();
        initThreeNodeStorage(ctx);

        // Use a builder class that's not registered
        @SuppressWarnings("unchecked")
        Class<? extends LoomBuilder<String>> unknownBuilder =
                (Class<? extends LoomBuilder<String>>) (Class<?>) LoomBuilder.class;

        Optional<String> result = ctx.getOptionalResultOf(unknownBuilder);
        assertThat(result).isEmpty();
    }

    @Test
    void getDependency_beforeStorage_throwsWithDiagnostics() {
        SpringBuilderContext ctx = createContext();
        initThreeNodeStorage(ctx);

        // Store Alpha so it appears in availableTypes, but don't store Beta
        ctx.storeResult(AlphaBuilder.class, Alpha.class, new Alpha("stored"));

        assertThatThrownBy(() -> ctx.getDependency(Beta.class))
                .isInstanceOf(LoomDependencyResolutionException.class)
                .satisfies(thrown -> {
                    LoomDependencyResolutionException ex = (LoomDependencyResolutionException) thrown;
                    assertThat(ex.getRequestedType()).isEqualTo("Beta");
                    assertThat(ex.getAvailableTypes()).contains("Alpha");
                    assertThat(ex.getCompletedBuilders()).contains("AlphaBuilder");
                });
    }
}
