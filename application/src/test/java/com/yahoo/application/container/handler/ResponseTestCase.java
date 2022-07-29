// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.application.container.handler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author Einar M R Rosenvinge
 */
public class ResponseTestCase {

    @Test
    void requireThatCharsetParsingWorks() {
        assertEquals("utf-8", Response.charset("text/foobar").toString().toLowerCase());
        assertEquals("utf-8", Response.charset("adsf").toString().toLowerCase());
        assertEquals("utf-8", Response.charset("").toString().toLowerCase());
        assertEquals("utf-8", Response.charset(null).toString().toLowerCase());

        assertEquals("us-ascii", Response.charset("something; charset=US-ASCII").toString().toLowerCase());
        assertEquals("iso-8859-1", Response.charset("something; charset=iso-8859-1").toString().toLowerCase());

        assertEquals("utf-8", Response.charset("something; charset=").toString().toLowerCase());
        assertEquals("utf-8", Response.charset("something; charset=bananarama").toString().toLowerCase());
    }

    @Test
    void testDefaultResponseBody() {
        Response res1 = new Response();
        Response res2 = new Response(new byte[0]);

        assertNotNull(res1.getBody());
        assertEquals(0, res1.getBody().length);
        assertNotNull(res2.getBody());
        assertEquals(0, res2.getBody().length);
    }
}
