package io.loom.core.annotation;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface LoomPassthrough {
    String method();
    String path();
    String upstream();
    String upstreamPath();
    String summary() default "";
    String description() default "";
    String[] tags() default {};
}
