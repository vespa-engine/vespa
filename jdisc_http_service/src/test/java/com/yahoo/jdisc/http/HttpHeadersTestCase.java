// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http;

import org.testng.annotations.Test;

import static org.testng.AssertJUnit.assertEquals;

/**
 * @author Simon Thoresen Hult
 */
public class HttpHeadersTestCase {

    @Test
    public void requireThatHeadersDoNotChange() {
        assertEquals("X-JDisc-Disable-Chunking", HttpHeaders.Names.X_DISABLE_CHUNKING);
    }
}
