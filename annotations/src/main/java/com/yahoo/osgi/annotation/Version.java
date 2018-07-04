// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.osgi.annotation;

/**
 * Version of an exported package
 * The default version is 1.0.0
 * @see <a href="http://www.osgi.org/javadoc/r4v43/org/osgi/framework/Version.html">Osgi version documentation</a>
 * @author Tony Vaagenes
 */
public @interface Version {

    /** must be non-negative **/
    int major() default 1;

    /** must be non-negative **/
    int minor() default 0;

    /** must be non-negative **/
    int micro() default 0;

    /** must follow the format (alpha|digit|'_'|'-')+ **/
    String qualifier() default "";

}
