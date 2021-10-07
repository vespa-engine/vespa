// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc;

import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.handler.CompletionHandler;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.text.Utf8;

/**
 * API test for ExtendedResponse.
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public class ExtendedResponseTestCase {

    private static final String COM_YAHOO_CONTAINER_JDISC_EXTENDED_RESPONSE_TEST_CASE = "com.yahoo.container.jdisc.ExtendedResponseTestCase";
    ExtendedResponse r;

    private static class TestResponse extends ExtendedResponse {

        public TestResponse(int status) {
            super(status);
        }


        @Override
        public void render(OutputStream output, ContentChannel networkChannel,
                CompletionHandler handler) throws IOException {
            // yes, this is sync rendering, so sue me :p
            try {
                output.write(Utf8.toBytes(COM_YAHOO_CONTAINER_JDISC_EXTENDED_RESPONSE_TEST_CASE));
            } finally {
                if (networkChannel != null) {
                    networkChannel.close(handler);
                }
            }
        }
    }


    @Before
    public void setUp() throws Exception {
        r = new TestResponse(Response.Status.OK);
    }

    @After
    public void tearDown() throws Exception {
        r = null;
    }

    @Test
    public final void testRenderOutputStreamContentChannelCompletionHandler() throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        r.render(b, null, null);
        assertEquals(COM_YAHOO_CONTAINER_JDISC_EXTENDED_RESPONSE_TEST_CASE, Utf8.toString(b.toByteArray()));
    }


    @Test
    public final void testGetParsedQuery() {
        assertNull(r.getParsedQuery());
    }

    @Test
    public final void testGetTiming() {
        assertNull(r.getTiming());
    }

    @Test
    public final void testGetCoverage() {
        assertNull(r.getCoverage());
    }

    @Test
    public final void testGetHitCounts() {
        assertNull(r.getHitCounts());
    }

}
