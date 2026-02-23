package io.loom.core.annotation;

import io.loom.core.interceptor.LoomInterceptor;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface LoomApi {
    String method();
    String path();
    Class<?> request() default void.class;
    Class<?> response() default void.class;
    Class<? extends LoomInterceptor>[] interceptors() default {};
    String summary() default "";
    String description() default "";
    String[] tags() default {};
    LoomQueryParam[] queryParams() default {};
    LoomHeaderParam[] headers() default {};
}
