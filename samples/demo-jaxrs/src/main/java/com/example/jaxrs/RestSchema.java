package com.example.jaxrs;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * ServiceComb REST Schema 占位注解，用于与 test1 代码风格一致（解析用）.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface RestSchema {
    String schemaId() default "";
}
