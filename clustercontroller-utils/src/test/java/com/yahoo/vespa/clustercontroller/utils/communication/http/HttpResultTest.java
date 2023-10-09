// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.utils.communication.http;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HttpResultTest {

    @Test
    void testSuccess() {
        assertFalse(new HttpResult().setHttpCode(199, "foo").isSuccess());
        assertTrue(new HttpResult().setHttpCode(200, "foo").isSuccess());
        assertTrue(new HttpResult().setHttpCode(299, "foo").isSuccess());
        assertFalse(new HttpResult().setHttpCode(300, "foo").isSuccess());
    }

    @Test
    void testToString() {
        assertEquals("HTTP 200/OK", new HttpResult().setContent("Foo").toString());
        assertEquals("HTTP 200/OK\n\nFoo", new HttpResult().setContent("Foo").toString(true));
        assertEquals("HTTP 200/OK", new HttpResult().toString(true));
        assertEquals("HTTP 200/OK", new HttpResult().setContent("").toString(true));
    }

    @Test
    void testNothingButGetCoverage() {
        new HttpResult().getHeaders();
    }

}
