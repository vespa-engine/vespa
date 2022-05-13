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

    int success() default 0;

    int inconclusive() default 0;

    int aborted() default 0;

    int skipped() default 0;

    int failure() default 0;

    int error() default 0;

}
