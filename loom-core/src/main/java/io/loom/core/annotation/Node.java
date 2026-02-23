package io.loom.core.annotation;

import io.loom.core.builder.LoomBuilder;
import java.lang.annotation.*;

@Target({})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Node {
    Class<? extends LoomBuilder<?>> builder();
    Class<? extends LoomBuilder<?>>[] dependsOn() default {};
    boolean required() default true;
    long timeoutMs() default 30000;
}
