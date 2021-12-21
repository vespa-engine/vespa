// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.subscription;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author Ulf Lilleengen
 */
public class ConfigInterruptedExceptionTest {

    @Test
    public void require_that_throwable_is_preserved() {
        ConfigInterruptedException e = new ConfigInterruptedException(new RuntimeException("foo"));
        assertEquals("foo", e.getCause().getMessage());
    }
}
