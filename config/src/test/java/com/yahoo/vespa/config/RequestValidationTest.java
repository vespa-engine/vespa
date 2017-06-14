// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config;

import com.yahoo.vespa.config.protocol.RequestValidation;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RequestValidationTest {

    @Test
    public void testVerifyName() {
        assertTrue(RequestValidation.verifyName("foo"));
        assertTrue(RequestValidation.verifyName("Foo"));
        assertTrue(RequestValidation.verifyName("foo-bar"));
        assertFalse(RequestValidation.verifyName("1foo"));
        assertTrue(RequestValidation.verifyName("foo_bar"));
    }

    @Test
    public void testVerifyDefMd5() {
        assertTrue(RequestValidation.verifyMd5(""));
        assertTrue(RequestValidation.verifyMd5("e8f0c01c7c3dcb8d3f62d7ff777fce6b"));
        assertTrue(RequestValidation.verifyMd5("e8f0c01c7c3dcb8d3f62d7ff777fce6B"));
        assertFalse(RequestValidation.verifyMd5("aaaaaaaaaaaaaaaaaa"));
        assertFalse(RequestValidation.verifyMd5("-8f0c01c7c3dcb8d3f62d7ff777fce6b"));
    }

    @Test
    public void testVerifyTimeout() {
        assertTrue(RequestValidation.verifyTimeout(1000L));
        assertFalse(RequestValidation.verifyTimeout(-1000L));
    }
}
