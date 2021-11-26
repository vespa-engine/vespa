// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.api.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * For annotating non-public API packages where we want to ensure API compatibility,
 *
 * Must be placed in a file called package-info.java in the package
 * @author mortent
 * @since 7.507
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PACKAGE)
@Documented
public @interface InternalApi {
}
