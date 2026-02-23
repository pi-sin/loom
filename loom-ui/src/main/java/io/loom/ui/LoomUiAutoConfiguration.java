package io.loom.ui;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;

@AutoConfiguration
@ConditionalOnProperty(prefix = "loom.ui", name = "enabled", havingValue = "true", matchIfMissing = true)
@ComponentScan(basePackages = "io.loom.ui")
public class LoomUiAutoConfiguration {
}
