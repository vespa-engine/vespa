// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.yahoo.jdisc.http.HttpRequest;
import com.yahoo.jdisc.http.servlet.ServletRequest;
import com.yahoo.jdisc.service.CurrentContainer;
import org.eclipse.jetty.util.Utf8Appendable;

import javax.servlet.http.HttpServletRequest;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.cert.X509Certificate;
import java.util.Enumeration;

import static com.yahoo.jdisc.Response.Status.BAD_REQUEST;
import static com.yahoo.jdisc.http.server.jetty.HttpServletRequestUtils.getConnection;
import static com.yahoo.jdisc.http.server.jetty.HttpServletRequestUtils.getConnectorLocalPort;

/**
 * @author Simon Thoresen Hult
 * @author bjorncs
 */
class HttpRequestFactory {

    public static HttpRequest newJDiscRequest(CurrentContainer container, HttpServletRequest servletRequest) {
        try {
            HttpRequest httpRequest = HttpRequest.newServerRequest(
                    container,
                    getUri(servletRequest),
                    HttpRequest.Method.valueOf(servletRequest.getMethod()),
                    HttpRequest.Version.fromString(servletRequest.getProtocol()),
                    new InetSocketAddress(servletRequest.getRemoteAddr(), servletRequest.getRemotePort()),
                    getConnection(servletRequest).getCreatedTimeStamp());
            httpRequest.context().put(ServletRequest.JDISC_REQUEST_X509CERT, getCertChain(servletRequest));
            return httpRequest;
        } catch (Utf8Appendable.NotUtf8Exception e) {
            throw createBadQueryException(e);
        }
    }

    // Implementation based on org.eclipse.jetty.server.Request.getRequestURL(), but with the connector's local port instead
    public static URI getUri(HttpServletRequest servletRequest) {
        try {
            String scheme = servletRequest.getScheme();
            String host = servletRequest.getServerName();
            int port = getConnectorLocalPort(servletRequest);
            String path = servletRequest.getRequestURI();
            String query = servletRequest.getQueryString();

            URI uri = URI.create(scheme + "://" +
                                 host + ":" + port +
                                 (path != null ? path : "") +
                                 (query != null ? "?" + query : ""));

            validateSchemeHostPort(scheme, host, port, uri);
            return uri;
        }
        catch (IllegalArgumentException e) {
            throw createBadQueryException(e);
        }
    }

    private static void validateSchemeHostPort(String scheme, String host, int port, URI uri) {
        if ( ! scheme.equals(uri.getScheme()))
            throw new IllegalArgumentException("Bad scheme: " + scheme);

        if ( ! host.equals(uri.getHost()) || port != uri.getPort())
            throw new IllegalArgumentException("Bad authority: " + uri.getRawAuthority() + " != " + host + ":" + port);
    }

    private static RequestException createBadQueryException(IllegalArgumentException e) {
        return new RequestException(BAD_REQUEST, "URL violates RFC 2396: " + e.getMessage(), e);
    }

    public static void copyHeaders(HttpServletRequest from, HttpRequest to) {
        for (Enumeration<String> it = from.getHeaderNames(); it.hasMoreElements(); ) {
            String key = it.nextElement();
            for (Enumeration<String> value = from.getHeaders(key); value.hasMoreElements(); ) {
                to.headers().add(key, value.nextElement());
            }
        }
    }

    private static X509Certificate[] getCertChain(HttpServletRequest servletRequest) {
        return (X509Certificate[]) servletRequest.getAttribute("javax.servlet.request.X509Certificate");
    }
}
