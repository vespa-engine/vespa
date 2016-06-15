// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http;

import org.testng.annotations.Test;

import static org.testng.AssertJUnit.assertEquals;

/**
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen Hult</a>
 */
public class HttpHeadersTestCase {

    @Test
    public void requireThatHeadersDoNotChange() {
        assertEquals("X-JDisc-Disable-Chunking", HttpHeaders.Names.X_DISABLE_CHUNKING);
        assertEquals("X-JDisc-Enable-TraceId", HttpHeaders.Names.X_ENABLE_TRACE_ID);
        assertEquals("X-JDisc-TraceId", HttpHeaders.Names.X_TRACE_ID);
    }
}
