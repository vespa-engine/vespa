// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.component.chain.dependencies;

import java.lang.annotation.*;

/**
 * Components or phases providing names contained in this list must be
 * placed earlier in the chain than the component that is annotated.
 * <p>
 * See {@link com.yahoo.component.chain.dependencies.ordering.ChainBuilder}
 * for dependency handling information.
 *
 * @author Tony Vaagenes
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Inherited
public @interface After {

    String[] value() default {};

}
