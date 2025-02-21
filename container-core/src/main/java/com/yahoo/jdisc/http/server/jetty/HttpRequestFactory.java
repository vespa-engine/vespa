// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.yahoo.jdisc.http.HttpRequest;
import com.yahoo.jdisc.service.CurrentContainer;
import org.bouncycastle.cert.ocsp.Req;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.server.Request;

import java.net.InetSocketAddress;
import java.net.URI;
import java.security.cert.X509Certificate;
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

    public static HttpRequest newJDiscRequest(CurrentContainer container, Request jettyRequest) {
        try {
            var jdiscHttpReq = HttpRequest.newServerRequest(
                    container,
                    getUri(jettyRequest),
                    getMethod(jettyRequest),
                    HttpRequest.Version.fromString(jettyRequest.getConnectionMetaData().getProtocol()),
                    new InetSocketAddress(Request.getRemoteAddr(jettyRequest), Request.getRemotePort(jettyRequest)),
                    getConnection(jettyRequest).getCreatedTimeStamp(),
                    Request.getTimeStamp(jettyRequest));
            jdiscHttpReq.context().put(RequestUtils.JDISC_REQUEST_X509CERT, getCertChain(jettyRequest));
            jdiscHttpReq.context().put(RequestUtils.JDICS_REQUEST_PORT, Request.getLocalPort(jettyRequest));
            var sslSessionData = (EndPoint.SslSessionData) jettyRequest.getAttribute(EndPoint.SslSessionData.ATTRIBUTE);
            if (sslSessionData != null) jdiscHttpReq.context().put(RequestUtils.JDISC_REQUEST_SSLSESSION, sslSessionData.sslSession());
            jettyRequest.setAttribute(HttpRequest.class.getName(), jdiscHttpReq);
            copyHeaders(jettyRequest, jdiscHttpReq);
            return jdiscHttpReq;
        } catch (IllegalArgumentException e) {
            throw createBadQueryException(e);
        }
    }

    private static HttpRequest.Method getMethod(Request jettyRequest) {
        String method = jettyRequest.getMethod();
        try {
            return HttpRequest.Method.valueOf(method);
        } catch (IllegalArgumentException e) {
            throw new RequestException(METHOD_NOT_ALLOWED, "Invalid method '" + method + "'");
        }
    }

    // Implementation based on org.eclipse.jetty.server.Request.getRequestURL(), but with the connector's local port instead
    public static URI getUri(Request jettyRequest) {
        try {
            String scheme = jettyRequest.getHttpURI().getScheme();
            String host = Request.getServerName(jettyRequest);
            int port = getConnectorLocalPort(jettyRequest);
            String path = jettyRequest.getHttpURI().getPath();
            String query = jettyRequest.getHttpURI().getQuery();

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

    public static void copyHeaders(Request jettyRequest, HttpRequest jdiscRequest) {
        jettyRequest.getHeaders()
                .forEach(header -> jdiscRequest.headers().add(header.getName(), header.getValueList()));
    }

    private static X509Certificate[] getCertChain(Request jettyRequest) {
        return Optional.ofNullable(jettyRequest.getAttribute(EndPoint.SslSessionData.ATTRIBUTE))
                .map(EndPoint.SslSessionData.class::cast)
                .map(EndPoint.SslSessionData::peerCertificates)
                .orElse(null);
    }
}
