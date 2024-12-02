// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.yahoo.jdisc.http.HttpRequest;
import com.yahoo.jdisc.service.CurrentContainer;
import jakarta.servlet.http.HttpServletRequest;
import org.eclipse.jetty.ee9.nested.Request;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.util.ssl.X509;

import java.net.InetSocketAddress;
import java.net.URI;
import java.security.cert.X509Certificate;
import java.util.Enumeration;
import java.util.Optional;

import static com.yahoo.jdisc.Response.Status.BAD_REQUEST;
import static com.yahoo.jdisc.Response.Status.METHOD_NOT_ALLOWED;
import static com.yahoo.jdisc.http.server.jetty.RequestUtils.getConnection;
import static com.yahoo.jdisc.http.server.jetty.RequestUtils.getConnectorLocalPort;

/**
 * @author Simon Thoresen Hult
 * @author bjorncs
 */
class HttpRequestFactory {

    public static HttpRequest newJDiscRequest(CurrentContainer container, HttpServletRequest servletRequest) {
        try {
            var jettyRequest = (Request) servletRequest;
            var jdiscHttpReq = HttpRequest.newServerRequest(
                    container,
                    getUri(servletRequest),
                    getMethod(servletRequest),
                    HttpRequest.Version.fromString(servletRequest.getProtocol()),
                    new InetSocketAddress(servletRequest.getRemoteAddr(), servletRequest.getRemotePort()),
                    getConnection(jettyRequest.getCoreRequest()).getCreatedTimeStamp(),
                    org.eclipse.jetty.server.Request.getTimeStamp(jettyRequest.getCoreRequest()));
            jdiscHttpReq.context().put(RequestUtils.JDISC_REQUEST_X509CERT, getCertChain(servletRequest));
            jdiscHttpReq.context().put(RequestUtils.JDICS_REQUEST_PORT, servletRequest.getLocalPort());
            var sslSessionData = (EndPoint.SslSessionData) jettyRequest.getAttribute(EndPoint.SslSessionData.ATTRIBUTE);
            if (sslSessionData != null) jdiscHttpReq.context().put(RequestUtils.JDISC_REQUEST_SSLSESSION, sslSessionData.sslSession());
            servletRequest.setAttribute(HttpRequest.class.getName(), jdiscHttpReq);
            return jdiscHttpReq;
        } catch (IllegalArgumentException e) {
            throw createBadQueryException(e);
        }
    }

    private static HttpRequest.Method getMethod(HttpServletRequest servletRequest) {
        String method = servletRequest.getMethod();
        try {
            return HttpRequest.Method.valueOf(method);
        } catch (IllegalArgumentException e) {
            throw new RequestException(METHOD_NOT_ALLOWED, "Invalid method '" + method + "'");
        }
    }

    // Implementation based on org.eclipse.jetty.server.Request.getRequestURL(), but with the connector's local port instead
    public static URI getUri(HttpServletRequest servletRequest) {
        try {
            String scheme = servletRequest.getScheme();
            String host = servletRequest.getServerName();
            int port = getConnectorLocalPort(((org.eclipse.jetty.ee9.nested.Request) servletRequest).getCoreRequest());
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
        var cause = e.getCause() != null ? e.getCause() : e;
        return new RequestException(BAD_REQUEST, "URL violates RFC 2396: " + cause.getMessage(), cause);
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
        return Optional.ofNullable(servletRequest.getAttribute(EndPoint.SslSessionData.ATTRIBUTE))
                .map(EndPoint.SslSessionData.class::cast)
                .map(EndPoint.SslSessionData::peerCertificates)
                .orElse(null);
    }
}
