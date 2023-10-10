// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config;

import com.yahoo.vespa.config.protocol.RequestValidation;
import org.junit.Test;

import static com.yahoo.vespa.config.PayloadChecksum.Type.MD5;
import static com.yahoo.vespa.config.PayloadChecksum.Type.XXHASH64;
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
        assertTrue(PayloadChecksum.empty(MD5).valid());
        assertTrue(new PayloadChecksum("e8f0c01c7c3dcb8d3f62d7ff777fce6b", MD5).valid());
        assertTrue(new PayloadChecksum("e8f0c01c7c3dcb8d3f62d7ff777fce6B", MD5).valid());
        assertTrue(new PayloadChecksum("e8f0c01c7c3dcb8d", XXHASH64).valid());
        assertFalse(new PayloadChecksum("-8f0c01c7c3dcb8d3f62d7ff777fce6b", MD5).valid());
    }

    @Test
    public void testVerifyTimeout() {
        assertTrue(RequestValidation.verifyTimeout(1000L));
        assertFalse(RequestValidation.verifyTimeout(-1000L));
    }

}
