package com.troupeforge.core.tool;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotates a record component in a tool's request or response record
 * to provide schema metadata for prompt generation.
 */
@Target(ElementType.RECORD_COMPONENT)
@Retention(RetentionPolicy.RUNTIME)
public @interface ToolParam {
    String description() default "";
    boolean required() default true;
}
