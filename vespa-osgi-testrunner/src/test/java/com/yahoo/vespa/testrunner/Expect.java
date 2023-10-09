// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.testrunner;

import org.junit.jupiter.api.Tag;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * @author jonmv
 */
@Retention(RetentionPolicy.RUNTIME)
@Tag("integration")
public @interface Expect {

    int status();

    long aborted() default 0;

    long skipped() default 0;

    long successful() default 0;

    long inconclusive() default 0;

    long failed() default 0;

    long error() default 0;

}
