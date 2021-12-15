// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.component.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Use this annotation to specify which constructor should be used when constructing a component.
 *
 * @author bjorncs
 */
@Target(CONSTRUCTOR)
@Retention(RUNTIME)
@Documented
public @interface Inject {}
