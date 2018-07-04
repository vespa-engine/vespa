// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.status;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Used to annotate methods that do not throw Exceptions.
 * They are still allowed to throw Errors, such as AssertionError
 *
 * TODO: move to vespajlib or find a suitable replacement
 * @author Tony Vaagenes
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.SOURCE)
@interface NoThrow {}

