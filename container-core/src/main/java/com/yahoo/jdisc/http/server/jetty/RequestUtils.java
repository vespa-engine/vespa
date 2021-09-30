// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import org.eclipse.jetty.http2.server.HTTP2ServerConnection;
import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.server.HttpConnection;
import org.eclipse.jetty.server.Request;

import javax.servlet.http.HttpServletRequest;

/**
 * @author bjorncs
 */
public class RequestUtils {
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
