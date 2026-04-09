// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.yolean.chain;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <p>Mark this component as providing some named functionality. Other components can then mark themselves as "before"
 * and "after" the string provided here, to impose constraints on ordering.</p>
 *
 * @deprecated Use com.yahoo.component.chain.dependencies.Provides instead.
 * @author Tony Vaagenes
 */
@Deprecated(forRemoval = true)
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Inherited
public @interface Provides {

    String[] value() default { };

}
