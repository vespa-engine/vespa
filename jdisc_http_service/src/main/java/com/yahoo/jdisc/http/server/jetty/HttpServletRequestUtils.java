// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import org.eclipse.jetty.server.HttpConnection;

import javax.servlet.http.HttpServletRequest;

/**
 * @author bjorncs
 */
public class HttpServletRequestUtils {
    private HttpServletRequestUtils() {}

    public static HttpConnection getConnection(HttpServletRequest request) {
        return (HttpConnection)request.getAttribute("org.eclipse.jetty.server.HttpConnection");
    }

    /**
     * Note: {@link HttpServletRequest#getLocalPort()} may return the local port of the load balancer / reverse proxy if proxy-protocol is enabled.
     * @return the actual local port of the underlying Jetty connector
     */
    public static int getConnectorLocalPort(HttpServletRequest request) {
        JDiscServerConnector connector = (JDiscServerConnector) getConnection(request).getConnector();
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
