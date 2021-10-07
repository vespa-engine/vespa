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
import com.yahoo.text.Utf8;

/**
 * API test for HttpResponse.
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public class HttpResponseTestCase {

    private static final String COM_YAHOO_CONTAINER_JDISC_HTTP_RESPONSE_TEST_CASE_TEST_RESPONSE = "com.yahoo.container.jdisc.HttpResponseTestCase.TestResponse";

    private static class TestResponse extends HttpResponse {

        public TestResponse(int status) {
            super(status);
        }

        @Override
        public void render(OutputStream outputStream) throws IOException {
            outputStream.write(Utf8.toBytes(COM_YAHOO_CONTAINER_JDISC_HTTP_RESPONSE_TEST_CASE_TEST_RESPONSE));
        }
    }

    HttpResponse r;

    @Before
    public void setUp() throws Exception {
        r = new TestResponse(Response.Status.OK);
    }

    @After
    public void tearDown() throws Exception {
        r = null;
    }

    @Test
    public final void testRender() throws IOException {
        ByteArrayOutputStream o = new ByteArrayOutputStream(1024);
        r.render(o);
        assertEquals(COM_YAHOO_CONTAINER_JDISC_HTTP_RESPONSE_TEST_CASE_TEST_RESPONSE, Utf8.toString(o.toByteArray()));
    }

    @Test
    public final void testGetStatus() {
        assertEquals(Response.Status.OK, r.getStatus());
    }

    @Test
    public final void testHeaders() {
        assertNotNull(r.headers());
    }

    @Test
    public final void testGetJdiscResponse() {
        assertNotNull(r.getJdiscResponse());
    }

    @Test
    public final void testGetContentType() {
        assertEquals(HttpResponse.DEFAULT_MIME_TYPE, r.getContentType());
    }

    @Test
    public final void testGetCharacterEncoding() {
        assertEquals(HttpResponse.DEFAULT_CHARACTER_ENCODING, r.getCharacterEncoding());
    }

}
