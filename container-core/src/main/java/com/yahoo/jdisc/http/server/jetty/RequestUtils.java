// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import jakarta.servlet.http.HttpServletRequest;
import org.eclipse.jetty.http2.server.HTTP2ServerConnection;
import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.server.HttpConnection;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.SecureRequestCustomizer;

/**
 * @author bjorncs
 */
public class RequestUtils {
    public static final String JDISC_REQUEST_X509CERT = "jdisc.request.X509Certificate";
    public static final String JDISC_REQUEST_SSLSESSION = "jdisc.request.SSLSession";
    public static final String JDISC_REQUEST_CHAIN = "jdisc.request.chain";
    public static final String JDISC_RESPONSE_CHAIN = "jdisc.response.chain";
    public static final String SERVLET_REQUEST_X509CERT = SecureRequestCustomizer.JAKARTA_SERVLET_REQUEST_X_509_CERTIFICATE;
    public static final String JETTY_REQUEST_SSLSESSION = new SecureRequestCustomizer().getSslSessionAttribute();

    // The local port as reported by servlet spec. This will be influenced by Host header and similar mechanisms.
    // The request URI uses the local listen port as the URI is used for handler routing/bindings.
    // Use this attribute for generating URIs that is presented to client.
    public static final String JDICS_REQUEST_PORT = "jdisc.request.port";

    private RequestUtils() {}

    public static Connection getConnection(Request request) {
        return request.getHttpChannel().getConnection();
    }

    public static JDiscServerConnector getConnector(Request request) {
        return (JDiscServerConnector) request.getHttpChannel().getConnector();
    }

    static boolean isHttpServerConnection(Connection connection) {
        return connection instanceof HttpConnection || connection instanceof HTTP2ServerConnection;
    }

    /**
     * Note: {@link HttpServletRequest#getLocalPort()} may return the local port of the load balancer / reverse proxy if proxy-protocol is enabled.
     * @return the actual local port of the underlying Jetty connector
     */
    public static int getConnectorLocalPort(Request request) {
        JDiscServerConnector connector = getConnector(request);
        int actualLocalPort = connector.getLocalPort();
        int localPortIfConnectorUnopened = -1;
        int localPortIfConnectorClosed = -2;
        if (actualLocalPort == localPortIfConnectorUnopened || actualLocalPort == localPortIfConnectorClosed) {
            int configuredLocalPort = connector.listenPort();
            int localPortEphemeralPort = 0;
            if (configuredLocalPort == localPortEphemeralPort) {
                throw new IllegalStateException("Unable to determine connector's listen port");
            }
            return configuredLocalPort;
        }
        return actualLocalPort;
    }

}
