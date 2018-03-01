// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.utils.communication.http;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class HttpResultTest {

    @Test
    public void testSuccess() {
        assertEquals(false, new HttpResult().setHttpCode(199, "foo").isSuccess());
        assertEquals(true, new HttpResult().setHttpCode(200, "foo").isSuccess());
        assertEquals(true, new HttpResult().setHttpCode(299, "foo").isSuccess());
        assertEquals(false, new HttpResult().setHttpCode(300, "foo").isSuccess());
    }

    @Test
    public void testToString() {
        assertEquals("HTTP 200/OK", new HttpResult().setContent("Foo").toString());
        assertEquals("HTTP 200/OK\n\nFoo", new HttpResult().setContent("Foo").toString(true));
        assertEquals("HTTP 200/OK", new HttpResult().toString(true));
        assertEquals("HTTP 200/OK", new HttpResult().setContent("").toString(true));
    }

    @Test
    public void testNothingButGetCoverage() {
        new HttpResult().getHeaders();
    }

}
