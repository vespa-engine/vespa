// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.classanalysis.sampleclasses;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Input for class analysis tests.
 * @author Tony Vaagenes
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface DummyAnnotation {
}
