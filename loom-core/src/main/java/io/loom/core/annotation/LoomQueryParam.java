package io.loom.core.annotation;

import java.lang.annotation.*;

@Target({})
@Retention(RetentionPolicy.RUNTIME)
public @interface LoomQueryParam {
    String name();
    Class<?> type() default String.class;
    boolean required() default false;
    String defaultValue() default "";
    String description() default "";
}
