// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.api.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Signifies that the annotated Java type/method/constructor is under development and may still change before they stabilize.
 * Should only be used on a type that part of a package annotated with {@link PublicApi}.
 *
 * @see <a href="https://docs.vespa.ai/en/vespa-versions.html">https://docs.vespa.ai/en/vespa-versions.html</a>
 *
 * @author bjorncs
 */
@Retention(RetentionPolicy.CLASS)
@Target({
        ElementType.CONSTRUCTOR,
        ElementType.METHOD,
        ElementType.TYPE,
        ElementType.FIELD
})
public @interface Beta {}
