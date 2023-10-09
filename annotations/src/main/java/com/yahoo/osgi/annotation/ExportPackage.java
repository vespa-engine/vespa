// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.osgi.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * For annotating packages that should be exported.
 *
 * Must be placed in a file called package-info.java in the
 * package that is to be exported.
 *
 * @author Tony Vaagenes
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.PACKAGE)
@Documented
public @interface ExportPackage {
    Version version() default @Version;
}
