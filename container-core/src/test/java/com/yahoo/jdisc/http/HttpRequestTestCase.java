// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http;

import com.yahoo.jdisc.Container;
import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.service.CurrentContainer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Simon Thoresen Hult
 */
public class HttpRequestTestCase {

    @Test
    void requireThatSimpleServerConstructorsUseReasonableDefaults() {
        URI uri = URI.create("http://localhost/");
        HttpRequest request = HttpRequest.newServerRequest(mockContainer(), uri);
        assertTrue(request.isServerRequest());
        assertEquals(uri, request.getUri());
        assertEquals(HttpRequest.Method.GET, request.getMethod());
        assertEquals(HttpRequest.Version.HTTP_1_1, request.getVersion());

        request = HttpRequest.newServerRequest(mockContainer(), uri, HttpRequest.Method.POST);
        assertTrue(request.isServerRequest());
        assertEquals(uri, request.getUri());
        assertEquals(HttpRequest.Method.POST, request.getMethod());
        assertEquals(HttpRequest.Version.HTTP_1_1, request.getVersion());

        request = HttpRequest.newServerRequest(mockContainer(), uri, HttpRequest.Method.POST, HttpRequest.Version.HTTP_1_0);
        assertTrue(request.isServerRequest());
        assertEquals(uri, request.getUri());
        assertEquals(HttpRequest.Method.POST, request.getMethod());
        assertEquals(HttpRequest.Version.HTTP_1_0, request.getVersion());
    }

    @Test
    void requireThatSimpleClientConstructorsUseReasonableDefaults() {
        Request parent = new Request(mockContainer(), URI.create("http://localhost/"));

        URI uri = URI.create("http://remotehost/");
        HttpRequest request = HttpRequest.newClientRequest(parent, uri);
        assertFalse(request.isServerRequest());
        assertEquals(uri, request.getUri());
        assertEquals(HttpRequest.Method.GET, request.getMethod());
        assertEquals(HttpRequest.Version.HTTP_1_1, request.getVersion());

        request = HttpRequest.newClientRequest(parent, uri, HttpRequest.Method.POST);
        assertFalse(request.isServerRequest());
        assertEquals(uri, request.getUri());
        assertEquals(HttpRequest.Method.POST, request.getMethod());
        assertEquals(HttpRequest.Version.HTTP_1_1, request.getVersion());

        request = HttpRequest.newClientRequest(parent, uri, HttpRequest.Method.POST, HttpRequest.Version.HTTP_1_0);
        assertFalse(request.isServerRequest());
        assertEquals(uri, request.getUri());
        assertEquals(HttpRequest.Method.POST, request.getMethod());
        assertEquals(HttpRequest.Version.HTTP_1_0, request.getVersion());
    }

    @Test
    void requireThatAccessorsWork() {
        URI uri = URI.create("http://localhost/path?foo=bar&foo=baz&cox=69");
        InetSocketAddress address = new InetSocketAddress("remotehost", 69);
        final HttpRequest request = HttpRequest.newServerRequest(mockContainer(), uri, HttpRequest.Method.GET,
                HttpRequest.Version.HTTP_1_1, address, 1L);
        assertEquals(uri, request.getUri());

        assertEquals(HttpRequest.Method.GET, request.getMethod());
        request.setMethod(HttpRequest.Method.CONNECT);
        assertEquals(HttpRequest.Method.CONNECT, request.getMethod());

        assertEquals(HttpRequest.Version.HTTP_1_1, request.getVersion());
        request.setVersion(HttpRequest.Version.HTTP_1_0);
        assertEquals(HttpRequest.Version.HTTP_1_0, request.getVersion());

        assertEquals(address, request.getRemoteAddress());
        request.setRemoteAddress(address = new InetSocketAddress("localhost", 96));
        assertEquals(address, request.getRemoteAddress());

        final URI proxy = URI.create("http://proxyhost/");
        request.setProxyServer(proxy);
        assertEquals(proxy, request.getProxyServer());

        assertNull(request.getConnectionTimeout(TimeUnit.MILLISECONDS));
        request.setConnectionTimeout(1, TimeUnit.SECONDS);
        assertEquals(Long.valueOf(1000), request.getConnectionTimeout(TimeUnit.MILLISECONDS));

        assertEquals(Arrays.asList("bar", "baz"), request.parameters().get("foo"));
        assertEquals(Collections.singletonList("69"), request.parameters().get("cox"));
        request.parameters().put("cox", Arrays.asList("6", "9"));
        assertEquals(Arrays.asList("bar", "baz"), request.parameters().get("foo"));
        assertEquals(Arrays.asList("6", "9"), request.parameters().get("cox"));

        assertEquals(1L, request.getConnectedAt(TimeUnit.MILLISECONDS));
    }

    @Test
    void requireThatHttp10EncodingIsNeverChunked() throws Exception {
        final HttpRequest request = newRequest(HttpRequest.Version.HTTP_1_0);
        assertFalse(request.isChunked());
        request.headers().add(HttpHeaders.Names.TRANSFER_ENCODING, HttpHeaders.Values.CHUNKED);
        assertFalse(request.isChunked());
    }

    @Test
    void requireThatHttp11EncodingIsNotChunkedByDefault() throws Exception {
        final HttpRequest request = newRequest(HttpRequest.Version.HTTP_1_1);
        assertFalse(request.isChunked());
    }

    @Test
    void requireThatHttp11EncodingCanBeChunked() throws Exception {
        final HttpRequest request = newRequest(HttpRequest.Version.HTTP_1_1);
        request.headers().add(HttpHeaders.Names.TRANSFER_ENCODING, HttpHeaders.Values.CHUNKED);
        assertTrue(request.isChunked());
    }

    @Test
    void requireThatHttp10ConnectionIsAlwaysClose() throws Exception {
        final HttpRequest request = newRequest(HttpRequest.Version.HTTP_1_0);
        assertFalse(request.isKeepAlive());
        request.headers().add(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
        assertTrue(request.isKeepAlive());
    }

    @Test
    void requireThatHttp11ConnectionIsKeepAliveByDefault() throws Exception {
        final HttpRequest request = newRequest(HttpRequest.Version.HTTP_1_1);
        assertTrue(request.isKeepAlive());
    }

    @Test
    void requireThatHttp11ConnectionCanBeClose() throws Exception {
        final HttpRequest request = newRequest(HttpRequest.Version.HTTP_1_1);
        request.headers().add(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.CLOSE);
        assertFalse(request.isKeepAlive());
    }

    @Test
    void requireThatHttp10NeverHasChunkedResponse() throws Exception {
        final HttpRequest request = newRequest(HttpRequest.Version.HTTP_1_0);
        assertFalse(request.hasChunkedResponse());
    }

    @Test
    void requireThatHttp11HasDefaultChunkedResponse() throws Exception {
        final HttpRequest request = newRequest(HttpRequest.Version.HTTP_1_1);
        assertTrue(request.hasChunkedResponse());
    }

    @Test
    void requireThatHttp11CanDisableChunkedResponse() throws Exception {
        final HttpRequest request = newRequest(HttpRequest.Version.HTTP_1_0);
        request.headers().add(com.yahoo.jdisc.http.HttpHeaders.Names.X_DISABLE_CHUNKING, "true");
        assertFalse(request.hasChunkedResponse());
    }

    @Test
    void requireThatCookieHeaderCanBeEncoded() throws Exception {
        final HttpRequest request = newRequest(HttpRequest.Version.HTTP_1_0);
        final List<Cookie> cookies = Collections.singletonList(new Cookie("foo", "bar"));
        request.encodeCookieHeader(cookies);
        final List<String> headers = request.headers().get(com.yahoo.jdisc.http.HttpHeaders.Names.COOKIE);
        assertEquals(1, headers.size());
        assertEquals(Cookie.toCookieHeader(cookies), headers.get(0));
    }

    @Test
    void requireThatCookieHeaderCanBeDecoded() throws Exception {
        final HttpRequest request = newRequest(HttpRequest.Version.HTTP_1_0);
        final List<Cookie> cookies = Collections.singletonList(new Cookie("foo", "bar"));
        request.encodeCookieHeader(cookies);
        assertEquals(cookies, request.decodeCookieHeader());
    }

    private static HttpRequest newRequest(final HttpRequest.Version version) throws Exception {
        return HttpRequest.newServerRequest(
                mockContainer(),
                new URI("http://localhost:1234/status.html"),
                HttpRequest.Method.GET,
                version);
    }

    private static CurrentContainer mockContainer() {
        final CurrentContainer currentContainer = mock(CurrentContainer.class);
        when(currentContainer.newReference(any(URI.class))).thenReturn(mock(Container.class));
        when(currentContainer.newReference(any(URI.class), any(Object.class))).thenReturn(mock(Container.class));
        return currentContainer;
    }
}
