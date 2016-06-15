// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.subscription;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author lulf
 * @since 5.1
 */
public class ConfigInterruptedExceptionTest {
    @Test
    public void require_that_throwable_is_preserved() {
        ConfigInterruptedException e = new ConfigInterruptedException(new RuntimeException("foo"));
        assertThat(e.getCause().getMessage(), is("foo"));
    }
}
