// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.test;

import org.testng.annotations.Test;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertTrue;
import static org.testng.AssertJUnit.fail;

/**
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen</a>
 */
public class RemoteServerTestCase {

    @Test
    public void requireThatRequestUriFactoryWorks() throws IOException {
        RemoteServer server = RemoteServer.newInstance();
        try {
            server.newRequestUri((String)null);
            fail();
        } catch (NullPointerException e) {

        }
        try {
            server.newRequestUri((URI)null);
            fail();
        } catch (NullPointerException e) {

        }
        try {
            server.newRequestUri("foo");
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(e.getCause() instanceof URISyntaxException);
        }
        URI requestUri = server.newRequestUri("/foo?baz=cox#bar");
        URI serverUri = server.connectionSpec();
        assertEquals(serverUri.getScheme(), requestUri.getScheme());
        assertEquals(serverUri.getUserInfo(), requestUri.getUserInfo());
        assertEquals(serverUri.getHost(), requestUri.getHost());
        assertEquals(serverUri.getPort(), requestUri.getPort());
        assertEquals("/foo", requestUri.getPath());
        assertEquals("baz=cox", requestUri.getQuery());
        assertEquals("bar", requestUri.getFragment());
        assertTrue(server.close(60, TimeUnit.SECONDS));
    }
}
