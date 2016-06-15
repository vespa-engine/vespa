// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.client;

import org.testng.annotations.Test;

import java.io.IOException;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertFalse;

/**
 * @author <a href="mailto:vikasp@yahoo-inc.com">Vikas Panwar</a>
 */
public class EmptyResponseTestCase {
    @Test(enabled = false)
    public void testGetterSetters() throws IOException {
        EmptyResponse underTest = EmptyResponse.INSTANCE;

        assertEquals(0, underTest.getStatusCode());
        assertNull(underTest.getStatusText());
        assertEquals(0, underTest.getResponseBodyAsByteBuffer().remaining());
        assertEquals(0, underTest.getResponseBodyAsBytes().length);
        assertNull(underTest.getResponseBodyAsStream());
        assertNull(underTest.getResponseBody());
        assertNull(underTest.getResponseBodyExcerpt(10, ""));
        assertNull(underTest.getResponseBodyExcerpt(10));
        assertNull(underTest.getResponseBody());
        assertNull(underTest.getUri());
        assertNull(underTest.getContentType());
        assertNull(underTest.getHeader(""));
        assertNull(underTest.getHeaders(""));
        assertNull(underTest.getHeaders());
        assertFalse(underTest.isRedirected());
        assertNull(underTest.getCookies());
        assertFalse(underTest.hasResponseStatus());
        assertFalse(underTest.hasResponseHeaders());
        assertFalse(underTest.hasResponseBody());
    }
}
