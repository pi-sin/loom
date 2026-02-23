package io.loom.core.annotation;

import java.lang.annotation.*;

@Target({})
@Retention(RetentionPolicy.RUNTIME)
public @interface LoomHeaderParam {
    String name();
    boolean required() default false;
    String description() default "";
}
