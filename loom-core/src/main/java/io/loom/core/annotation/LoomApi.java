package io.loom.core.annotation;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface LoomApi {
    String method();
    String path();
    Class<?> request() default void.class;
    Class<?> response() default void.class;
    String[] middlewares() default {};
    String[] guards() default {};
    String summary() default "";
    String description() default "";
    String[] tags() default {};
    LoomQueryParam[] queryParams() default {};
    LoomHeaderParam[] headers() default {};
}
