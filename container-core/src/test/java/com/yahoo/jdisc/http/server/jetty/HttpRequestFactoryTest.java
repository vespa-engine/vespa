// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.yahoo.jdisc.Container;
import com.yahoo.jdisc.References;
import com.yahoo.jdisc.ResourceReference;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.handler.RequestHandler;
import com.yahoo.jdisc.http.HttpRequest;
import com.yahoo.jdisc.service.CurrentContainer;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author Steinar Knutsen
 * @author bjorncs
 */
public class HttpRequestFactoryTest {

    private static final int LOCAL_PORT = 80;

    @Test
    void testLegalURIs() {
        {
            URI uri = HttpRequestFactory.getUri(createMockRequest("https", "host", null, null));
            assertEquals("https", uri.getScheme());
            assertEquals("host", uri.getHost());
            assertEquals("", uri.getRawPath());
            assertNull(uri.getRawQuery());
        }
        {
            URI uri = HttpRequestFactory.getUri(createMockRequest("https", "host", "", ""));
            assertEquals("https", uri.getScheme());
            assertEquals("host", uri.getHost());
            assertEquals("", uri.getRawPath());
            assertEquals("", uri.getRawQuery());
        }
        {
            URI uri = HttpRequestFactory.getUri(createMockRequest("http", "host.a1-2-3", "", ""));
            assertEquals("http", uri.getScheme());
            assertEquals("host.a1-2-3", uri.getHost());
            assertEquals("", uri.getRawPath());
            assertEquals("", uri.getRawQuery());
        }
        {
            URI uri = HttpRequestFactory.getUri(createMockRequest("https", "host", "/:1/../1=.", ""));
            assertEquals("https", uri.getScheme());
            assertEquals("host", uri.getHost());
            assertEquals("/:1/../1=.", uri.getRawPath());
            assertEquals("", uri.getRawQuery());
        }
        {
            URI uri = HttpRequestFactory.getUri(createMockRequest("https", "host", "", "a=/../&?="));
            assertEquals("https", uri.getScheme());
            assertEquals("host", uri.getHost());
            assertEquals("", uri.getRawPath());
            assertEquals("a=/../&?=", uri.getRawQuery());
        }
    }

    @Test
    void testIllegalQuery() {
        try {
            HttpRequestFactory.newJDiscRequest(
                    new MockContainer(),
                    createMockRequest("http", "example.com", "/search", "query=\"contains_quotes\""));
            fail("Above statement should throw");
        } catch (RequestException e) {
            assertThat(e.getResponseStatus(), is(Response.Status.BAD_REQUEST));
        }
    }

    @Test
    final void illegal_host_throws_requestexception1() {
        try {
            HttpRequestFactory.newJDiscRequest(
                    new MockContainer(),
                    createMockRequest("http", "?", "/foo", ""));
            fail("Above statement should throw");
        } catch (RequestException e) {
            assertThat(e.getResponseStatus(), is(Response.Status.BAD_REQUEST));
        }
    }

    @Test
    final void illegal_host_throws_requestexception2() {
        try {
            HttpRequestFactory.newJDiscRequest(
                    new MockContainer(),
                    createMockRequest("http", ".", "/foo", ""));
            fail("Above statement should throw");
        } catch (RequestException e) {
            assertThat(e.getResponseStatus(), is(Response.Status.BAD_REQUEST));
        }
    }

    @Test
    final void illegal_host_throws_requestexception3() {
        try {
            HttpRequestFactory.newJDiscRequest(
                    new MockContainer(),
                    createMockRequest("http", "*", "/foo", ""));
            fail("Above statement should throw");
        } catch (RequestException e) {
            assertThat(e.getResponseStatus(), is(Response.Status.BAD_REQUEST));
        }
    }

    @Test
    final void illegal_unicode_in_query_throws_requestexception() {
        try {
            HttpRequestFactory.newJDiscRequest(
                    new MockContainer(),
                    createMockRequest("http", "example.com", "/search", "query=%c0%ae"));
            fail("Above statement should throw");
        } catch (RequestException e) {
            assertThat(e.getResponseStatus(), is(Response.Status.BAD_REQUEST));
            assertThat(e.getMessage(), equalTo("URL violates RFC 2396: Not valid UTF8! byte C0 in state 0"));
        }
    }

    @Test
    void request_uri_uses_local_port() {
        HttpRequest request = HttpRequestFactory.newJDiscRequest(
                new MockContainer(),
                createMockRequest("https", "example.com", "/search", "query=value"));
        assertEquals(LOCAL_PORT, request.getUri().getPort());
    }

    private HttpServletRequest createMockRequest(String scheme, String host, String path, String query) {
        return JettyMockRequestBuilder.newBuilder()
                .uri(scheme, host, LOCAL_PORT, path, query)
                .remote("127.0.0.1", "localhost", 1234)
                .localPort(LOCAL_PORT)
                .build();
    }


    private static final class MockContainer implements CurrentContainer {

        @Override
        public Container newReference(URI uri) {
            return new Container() {

                @Override
                public RequestHandler resolveHandler(com.yahoo.jdisc.Request request) {
                    return null;
                }

                @Override
                public <T> T getInstance(Class<T> tClass) {
                    return null;
                }

                @Override
                public ResourceReference refer() {
                    return References.NOOP_REFERENCE;
                }

                @Override
                public void release() {

                }

                @Override
                public long currentTimeMillis() {
                    return 0;
                }
            };
        }
    }

}
