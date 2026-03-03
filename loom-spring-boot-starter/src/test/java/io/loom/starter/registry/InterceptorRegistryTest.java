package io.loom.starter.registry;

import io.loom.core.interceptor.InterceptorChain;
import io.loom.core.interceptor.LoomGlobalInterceptor;
import io.loom.core.interceptor.LoomHttpContext;
import io.loom.core.interceptor.LoomInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InterceptorRegistryTest {

    private ApplicationContext applicationContext;

    // --- Test interceptor stubs ---

    static class GlobalAuth implements LoomGlobalInterceptor {
        @Override
        public void handle(LoomHttpContext context, InterceptorChain chain) {
            chain.next(context);
        }

        @Override
        public int order() { return 0; }
    }

    static class GlobalLogging implements LoomGlobalInterceptor {
        @Override
        public void handle(LoomHttpContext context, InterceptorChain chain) {
            chain.next(context);
        }

        @Override
        public int order() { return 1; }
    }

    static class PerApiRateLimit implements LoomInterceptor {
        @Override
        public void handle(LoomHttpContext context, InterceptorChain chain) {
            chain.next(context);
        }

        @Override
        public int order() { return 2; }
    }

    static class PerApiApiKey implements LoomInterceptor {
        @Override
        public void handle(LoomHttpContext context, InterceptorChain chain) {
            chain.next(context);
        }

        @Override
        public int order() { return -1; }
    }

    @BeforeEach
    void setUp() {
        applicationContext = mock(ApplicationContext.class);
    }

    @Test
    void onlyGlobalInterceptorsAppearInGlobalList() {
        when(applicationContext.getBeansOfType(LoomInterceptor.class)).thenReturn(Map.of(
                "globalAuth", new GlobalAuth(),
                "globalLogging", new GlobalLogging(),
                "rateLimit", new PerApiRateLimit()
        ));

        InterceptorRegistry registry = new InterceptorRegistry(applicationContext);

        List<LoomInterceptor> globals = registry.getGlobalInterceptors();
        assertThat(globals).hasSize(2);
        assertThat(globals).allMatch(i -> i instanceof LoomGlobalInterceptor);
    }

    @Test
    void globalInterceptorsSortedByOrder() {
        when(applicationContext.getBeansOfType(LoomInterceptor.class)).thenReturn(Map.of(
                "globalLogging", new GlobalLogging(),
                "globalAuth", new GlobalAuth()
        ));

        InterceptorRegistry registry = new InterceptorRegistry(applicationContext);

        List<LoomInterceptor> globals = registry.getGlobalInterceptors();
        assertThat(globals).hasSize(2);
        assertThat(globals.get(0)).isInstanceOf(GlobalAuth.class);
        assertThat(globals.get(1)).isInstanceOf(GlobalLogging.class);
    }

    @Test
    void perApiInterceptorsAvailableViaGetInterceptors() {
        PerApiRateLimit rateLimit = new PerApiRateLimit();
        when(applicationContext.getBeansOfType(LoomInterceptor.class)).thenReturn(Map.of(
                "globalAuth", new GlobalAuth(),
                "rateLimit", rateLimit
        ));

        InterceptorRegistry registry = new InterceptorRegistry(applicationContext);

        @SuppressWarnings("unchecked")
        Class<? extends LoomInterceptor>[] classes = new Class[]{PerApiRateLimit.class};
        List<LoomInterceptor> interceptors = registry.getInterceptors(classes);

        assertThat(interceptors).hasSize(2);
        assertThat(interceptors).anyMatch(i -> i instanceof GlobalAuth);
        assertThat(interceptors).anyMatch(i -> i instanceof PerApiRateLimit);
    }

    @Test
    void getInterceptorsWithNullClassesReturnsOnlyGlobals() {
        when(applicationContext.getBeansOfType(LoomInterceptor.class)).thenReturn(Map.of(
                "globalAuth", new GlobalAuth(),
                "rateLimit", new PerApiRateLimit()
        ));

        InterceptorRegistry registry = new InterceptorRegistry(applicationContext);

        List<LoomInterceptor> interceptors = registry.getInterceptors(null);
        assertThat(interceptors).hasSize(1);
        assertThat(interceptors.get(0)).isInstanceOf(GlobalAuth.class);
    }

    @Test
    void getInterceptorsWithEmptyClassesReturnsOnlyGlobals() {
        when(applicationContext.getBeansOfType(LoomInterceptor.class)).thenReturn(Map.of(
                "globalAuth", new GlobalAuth(),
                "rateLimit", new PerApiRateLimit()
        ));

        InterceptorRegistry registry = new InterceptorRegistry(applicationContext);

        @SuppressWarnings("unchecked")
        Class<? extends LoomInterceptor>[] classes = new Class[0];
        List<LoomInterceptor> interceptors = registry.getInterceptors(classes);
        assertThat(interceptors).hasSize(1);
        assertThat(interceptors.get(0)).isInstanceOf(GlobalAuth.class);
    }

    @Test
    void globalAndPerApiDeduplication() {
        GlobalAuth globalAuth = new GlobalAuth();
        when(applicationContext.getBeansOfType(LoomInterceptor.class)).thenReturn(Map.of(
                "globalAuth", globalAuth
        ));

        InterceptorRegistry registry = new InterceptorRegistry(applicationContext);

        // Request per-API list that includes GlobalAuth — should not duplicate it
        @SuppressWarnings("unchecked")
        Class<? extends LoomInterceptor>[] classes = new Class[]{GlobalAuth.class};
        List<LoomInterceptor> interceptors = registry.getInterceptors(classes);

        assertThat(interceptors).hasSize(1);
        assertThat(interceptors.get(0)).isSameAs(globalAuth);
    }

    @Test
    void combinedListSortedByOrder() {
        when(applicationContext.getBeansOfType(LoomInterceptor.class)).thenReturn(Map.of(
                "globalLogging", new GlobalLogging(),
                "globalAuth", new GlobalAuth(),
                "apiKey", new PerApiApiKey(),
                "rateLimit", new PerApiRateLimit()
        ));

        InterceptorRegistry registry = new InterceptorRegistry(applicationContext);

        @SuppressWarnings("unchecked")
        Class<? extends LoomInterceptor>[] classes = new Class[]{PerApiApiKey.class, PerApiRateLimit.class};
        List<LoomInterceptor> interceptors = registry.getInterceptors(classes);

        assertThat(interceptors).hasSize(4);
        // order: -1 (PerApiApiKey), 0 (GlobalAuth), 1 (GlobalLogging), 2 (PerApiRateLimit)
        assertThat(interceptors.get(0)).isInstanceOf(PerApiApiKey.class);
        assertThat(interceptors.get(1)).isInstanceOf(GlobalAuth.class);
        assertThat(interceptors.get(2)).isInstanceOf(GlobalLogging.class);
        assertThat(interceptors.get(3)).isInstanceOf(PerApiRateLimit.class);
    }

    @Test
    void isGlobalReturnsTrueForGlobalInterceptors() {
        GlobalAuth globalAuth = new GlobalAuth();
        PerApiRateLimit rateLimit = new PerApiRateLimit();
        when(applicationContext.getBeansOfType(LoomInterceptor.class)).thenReturn(Map.of(
                "globalAuth", globalAuth,
                "rateLimit", rateLimit
        ));

        InterceptorRegistry registry = new InterceptorRegistry(applicationContext);

        assertThat(registry.isGlobal(globalAuth)).isTrue();
        assertThat(registry.isGlobal(rateLimit)).isFalse();
    }

    @Test
    void emptyGlobalListWhenNoGlobalInterceptorsExist() {
        when(applicationContext.getBeansOfType(LoomInterceptor.class)).thenReturn(Map.of(
                "rateLimit", new PerApiRateLimit(),
                "apiKey", new PerApiApiKey()
        ));

        InterceptorRegistry registry = new InterceptorRegistry(applicationContext);

        assertThat(registry.getGlobalInterceptors()).isEmpty();
    }

    @Test
    void emptyRegistryWhenNoInterceptorsExist() {
        when(applicationContext.getBeansOfType(LoomInterceptor.class)).thenReturn(Map.of());

        InterceptorRegistry registry = new InterceptorRegistry(applicationContext);

        assertThat(registry.getGlobalInterceptors()).isEmpty();
        assertThat(registry.getInterceptors(null)).isEmpty();
    }
}
