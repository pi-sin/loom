package io.loom.starter.config;

import io.loom.core.annotation.LoomApi;
import io.loom.core.annotation.LoomPassthrough;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.BeanDefinitionReaderUtils;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

public class LoomBeanRegistry implements BeanDefinitionRegistryPostProcessor {

    private final String[] basePackages;

    public LoomBeanRegistry(String[] basePackages) {
        this.basePackages = basePackages;
    }

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(LoomApi.class));
        scanner.addIncludeFilter(new AnnotationTypeFilter(LoomPassthrough.class));

        for (String basePackage : basePackages) {
            for (var bd : scanner.findCandidateComponents(basePackage)) {
                if (!registry.containsBeanDefinition(bd.getBeanClassName())) {
                    registry.registerBeanDefinition(
                            BeanDefinitionReaderUtils.generateBeanName(bd, registry),
                            bd);
                }
            }
        }
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        // no-op
    }
}
