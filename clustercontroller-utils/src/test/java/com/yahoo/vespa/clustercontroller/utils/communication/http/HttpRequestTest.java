// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.utils.communication.http;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

public class HttpRequestTest {

    private HttpRequest createRequest() {
        return new HttpRequest()
                .setHost("local")
                .setPort(20)
                .addHttpHeader("x-foo", "bar")
                .setPath("/bah")
                .setHttpOperation(HttpRequest.HttpOp.PUT)
                .addUrlOption("urk", "arg")
                .setTimeout(25);
    }

    @Test
    public void testEquality() {
        assertEquals(createRequest(), createRequest());
        assertNotSame(createRequest(), createRequest().setHost("localhost"));
        assertNotSame(createRequest(), createRequest().setPort(40));
        assertNotSame(createRequest(), createRequest().setPath("/hmm"));
        assertNotSame(createRequest(), createRequest().addHttpHeader("dsf", "fs"));
        assertNotSame(createRequest(), createRequest().setHttpOperation(HttpRequest.HttpOp.DELETE));
    }

    @Test
    public void testVerifyComplete() {
        // To be a complete request, an HTTP request must include:
        //  - A path
        //  - The HTTP operation type
        try{
            new HttpRequest().setPath("/foo").verifyComplete();
            assertTrue(false);
        } catch (IllegalStateException e) {
        }
        try{
            new HttpRequest().setHttpOperation(HttpRequest.HttpOp.GET).verifyComplete();
            assertTrue(false);
        } catch (IllegalStateException e) {
        }
        new HttpRequest().setHttpOperation(HttpRequest.HttpOp.GET).setPath("/bar").verifyComplete();
    }

    @Test
    public void testMerge() {
        {
            HttpRequest base = new HttpRequest()
                    .setHttpOperation(HttpRequest.HttpOp.POST)
                    .addUrlOption("hmm", "arg")
                    .addHttpHeader("x-foo", "bar");
            HttpRequest req = new HttpRequest()
                    .addUrlOption("hmm", "arg")
                    .addHttpHeader("x-foo", "bar");
            HttpRequest merged = base.merge(req);

            HttpRequest expected = new HttpRequest()
                    .setHttpOperation(HttpRequest.HttpOp.POST)
                    .addUrlOption("hmm", "arg")
                    .addHttpHeader("x-foo", "bar");
            assertEquals(expected, merged);
        }
        {
            HttpRequest base = new HttpRequest()
                    .setHttpOperation(HttpRequest.HttpOp.POST)
                    .addHttpHeader("x-foo", "bar")
                    .addUrlOption("hmm", "arg");
            HttpRequest req = new HttpRequest()
                    .setHttpOperation(HttpRequest.HttpOp.PUT)
                    .setPath("/gohere")
                    .addHttpHeader("Content-Type", "whatevah")
                    .addUrlOption("tit", "tat")
                    .setPostContent("foo");
            HttpRequest merged = base.merge(req);

            HttpRequest expected = new HttpRequest()
                    .setHttpOperation(HttpRequest.HttpOp.PUT)
                    .setPath("/gohere")
                    .addHttpHeader("x-foo", "bar")
                    .addHttpHeader("Content-Type", "whatevah")
                    .addUrlOption("hmm", "arg")
                    .addUrlOption("tit", "tat")
                    .setPostContent("foo");
            assertEquals(expected, merged);
        }
    }

    @Test
    public void testNonExistingHeader() {
        assertEquals("foo", new HttpRequest().getHeader("asd", "foo"));
        assertEquals("foo", new HttpRequest().addHttpHeader("bar", "foo").getHeader("asd", "foo"));
    }

    @Test
    public void testOption() {
        assertEquals("bar", new HttpRequest().addUrlOption("foo", "bar").getOption("foo", "foo"));
        assertEquals("foo", new HttpRequest().getOption("asd", "foo"));
    }

    @Test
    public void testToString() {
        assertEquals("GET? http://localhost:8080/",
                     new HttpRequest()
                             .setHost("localhost")
                             .setPort(8080)
                             .toString(true));
        assertEquals("POST http://localhost/",
                new HttpRequest()
                        .setHttpOperation(HttpRequest.HttpOp.POST)
                        .setHost("localhost")
                        .toString(true));
        assertEquals("GET http://localhost/?foo=bar",
                new HttpRequest()
                        .setHttpOperation(HttpRequest.HttpOp.GET)
                        .addUrlOption("foo", "bar")
                        .setHost("localhost")
                        .toString(true));
    }

    @Test
    public void testNothingButGetCoverage() {
        assertEquals(false, new HttpRequest().equals(new Object()));
        new HttpRequest().getHeaders();
        new HttpRequest().setUrlOptions(new HttpRequest().getUrlOptions());
    }

}
