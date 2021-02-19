// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.component.chain.dependencies;

import java.lang.annotation.*;

/**
 * Mark this component as providing some named functionality.
 * Other components can then mark themselves as "before" and "after" the string provided here,
 * to impose constraints on ordering.
 *
 * @author Tony Vaagenes
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Inherited
public @interface Provides {

    String[] value() default {};

}
