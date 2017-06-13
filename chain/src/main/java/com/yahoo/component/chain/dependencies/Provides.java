// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.component.chain.dependencies;

import java.lang.annotation.*;

/**
 * Mark this component as providing some named functionality.
 * Other components can then mark themselves as "before" and "after" the string provided here,
 * to impose constraints on ordering.
 *
 * @author  tonytv
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Inherited
public @interface Provides {
    public abstract String[] value() default {};
}
