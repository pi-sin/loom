package io.loom.starter.context;

import io.loom.core.builder.LoomBuilder;
import io.loom.core.registry.BuilderFactory;
import org.springframework.context.ApplicationContext;

public class SpringBuilderFactory implements BuilderFactory {

    private final ApplicationContext applicationContext;

    public SpringBuilderFactory(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> LoomBuilder<T> createBuilder(Class<? extends LoomBuilder<T>> builderClass) {
        return applicationContext.getBean(builderClass);
    }

    @Override
    public LoomBuilder<?> createBuilderUntyped(Class<? extends LoomBuilder<?>> builderClass) {
        return applicationContext.getBean(builderClass);
    }
}
