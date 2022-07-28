// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http;

import com.yahoo.jdisc.Container;
import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.service.CurrentContainer;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Simon Thoresen Hult
 */
public class HttpResponseTestCase {

    @Test
    void requireThatAccessorsWork() throws Exception {
        final HttpResponse response = newResponse(6, "foo");
        assertEquals(6, response.getStatus());
        assertEquals("foo", response.getMessage());
        assertNull(response.getError());
        assertTrue(response.isChunkedEncodingEnabled());

        response.setStatus(9);
        assertEquals(9, response.getStatus());

        response.setMessage("bar");
        assertEquals("bar", response.getMessage());

        final Throwable err = new Throwable();
        response.setError(err);
        assertSame(err, response.getError());

        response.setChunkedEncodingEnabled(false);
        assertFalse(response.isChunkedEncodingEnabled());
    }

    @Test
    void requireThatStatusCodesDoNotChange() {
        assertEquals(HttpResponse.Status.CREATED, 201);
        assertEquals(HttpResponse.Status.ACCEPTED, 202);
        assertEquals(HttpResponse.Status.NON_AUTHORITATIVE_INFORMATION, 203);
        assertEquals(HttpResponse.Status.NO_CONTENT, 204);
        assertEquals(HttpResponse.Status.RESET_CONTENT, 205);
        assertEquals(HttpResponse.Status.PARTIAL_CONTENT, 206);

        assertEquals(HttpResponse.Status.MULTIPLE_CHOICES, 300);
        assertEquals(HttpResponse.Status.SEE_OTHER, 303);
        assertEquals(HttpResponse.Status.NOT_MODIFIED, 304);
        assertEquals(HttpResponse.Status.USE_PROXY, 305);

        assertEquals(HttpResponse.Status.PAYMENT_REQUIRED, 402);
        assertEquals(HttpResponse.Status.PROXY_AUTHENTICATION_REQUIRED, 407);
        assertEquals(HttpResponse.Status.CONFLICT, 409);
        assertEquals(HttpResponse.Status.GONE, 410);
        assertEquals(HttpResponse.Status.LENGTH_REQUIRED, 411);
        assertEquals(HttpResponse.Status.PRECONDITION_FAILED, 412);
        assertEquals(HttpResponse.Status.REQUEST_ENTITY_TOO_LARGE, 413);
        assertEquals(HttpResponse.Status.REQUEST_URI_TOO_LONG, 414);
        assertEquals(HttpResponse.Status.UNSUPPORTED_MEDIA_TYPE, 415);
        assertEquals(HttpResponse.Status.REQUEST_RANGE_NOT_SATISFIABLE, 416);
        assertEquals(HttpResponse.Status.EXPECTATION_FAILED, 417);

        assertEquals(HttpResponse.Status.BAD_GATEWAY, 502);
        assertEquals(HttpResponse.Status.GATEWAY_TIMEOUT, 504);
    }

    @Test
    void requireThat5xxIsServerError() {
        for (int i = 0; i < 999; ++i) {
            assertEquals(i >= 500 && i < 600, HttpResponse.isServerError(new Response(i)));
        }
    }

    @Test
    void requireThatCookieHeaderCanBeEncoded() throws Exception {
        final HttpResponse response = newResponse(69, "foo");
        final List<Cookie> cookies = Collections.singletonList(new Cookie("foo", "bar"));
        response.encodeSetCookieHeader(cookies);
        final List<String> headers = response.headers().get(HttpHeaders.Names.SET_COOKIE);
        assertEquals(1, headers.size());
        assertEquals(Cookie.toSetCookieHeaders(cookies), headers);
    }

    @Test
    void requireThatMultipleCookieHeadersCanBeEncoded() throws Exception {
        final HttpResponse response = newResponse(69, "foo");
        final List<Cookie> cookies = Arrays.asList(new Cookie("foo", "bar"), new Cookie("baz", "cox"));
        response.encodeSetCookieHeader(cookies);
        final List<String> headers = response.headers().get(HttpHeaders.Names.SET_COOKIE);
        assertEquals(2, headers.size());
        assertEquals(Cookie.toSetCookieHeaders(Arrays.asList(new Cookie("foo", "bar"), new Cookie("baz", "cox"))),
                headers);
    }

    @Test
    void requireThatCookieHeaderCanBeDecoded() throws Exception {
        final HttpResponse response = newResponse(69, "foo");
        final List<Cookie> cookies = Collections.singletonList(new Cookie("foo", "bar"));
        response.encodeSetCookieHeader(cookies);
        assertEquals(cookies, response.decodeSetCookieHeader());
    }

    @Test
    void requireThatMultipleCookieHeadersCanBeDecoded() throws Exception {
        final HttpResponse response = newResponse(69, "foo");
        final List<Cookie> cookies = Arrays.asList(new Cookie("foo", "bar"), new Cookie("baz", "cox"));
        response.encodeSetCookieHeader(cookies);
        assertEquals(cookies, response.decodeSetCookieHeader());
    }

    private static HttpResponse newResponse(final int status, final String message) throws Exception {
        final Request request = HttpRequest.newServerRequest(
                mockContainer(),
                new URI("http://localhost:1234/status.html"),
                HttpRequest.Method.GET,
                HttpRequest.Version.HTTP_1_1);
        return HttpResponse.newInstance(status, message);
    }

    private static CurrentContainer mockContainer() {
        final CurrentContainer currentContainer = mock(CurrentContainer.class);
        when(currentContainer.newReference(any(URI.class))).thenReturn(mock(Container.class));
        when(currentContainer.newReference(any(URI.class), any(Object.class))).thenReturn(mock(Container.class));
        return currentContainer;
    }
}
