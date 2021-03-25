// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.http.ConnectorConfig;
import com.yahoo.jdisc.http.servlet.ServletRequest;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.HandlerWrapper;

import javax.servlet.DispatcherType;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.yahoo.jdisc.http.server.jetty.HttpServletRequestUtils.getConnectorLocalPort;

/**
 * A Jetty handler that enforces TLS client authentication with configurable white list.
 *
 * @author bjorncs
 */
class TlsClientAuthenticationEnforcer extends HandlerWrapper {

    private final Map<Integer, List<String>> portToWhitelistedPathsMapping;

    TlsClientAuthenticationEnforcer(List<ConnectorConfig> connectorConfigs) {
        portToWhitelistedPathsMapping = createWhitelistMapping(connectorConfigs);
    }

    @Override
    public void handle(String target, Request request, HttpServletRequest servletRequest, HttpServletResponse servletResponse) throws IOException, ServletException {
        if (isHttpsRequest(request)
                && !isRequestToWhitelistedBinding(servletRequest)
                && !isClientAuthenticated(servletRequest)) {
            servletResponse.sendError(
                    Response.Status.UNAUTHORIZED,
                    "Client did not present a x509 certificate, " +
                            "or presented a certificate not issued by any of the CA certificates in trust store.");
        } else {
            _handler.handle(target, request, servletRequest, servletResponse);
        }
    }

    private static Map<Integer, List<String>> createWhitelistMapping(List<ConnectorConfig> connectorConfigs) {
        var mapping = new HashMap<Integer, List<String>>();
        for (ConnectorConfig connectorConfig : connectorConfigs) {
            var enforcerConfig = connectorConfig.tlsClientAuthEnforcer();
            if (enforcerConfig.enable()) {
                mapping.put(connectorConfig.listenPort(), enforcerConfig.pathWhitelist());
            }
        }
        return mapping;
    }

    private boolean isHttpsRequest(Request request) {
        return request.getDispatcherType() == DispatcherType.REQUEST && request.getScheme().equalsIgnoreCase("https");
    }

    private boolean isRequestToWhitelistedBinding(HttpServletRequest servletRequest) {
        int localPort = getConnectorLocalPort(servletRequest);
        List<String> whiteListedPaths = getWhitelistedPathsForPort(localPort);
        if (whiteListedPaths == null) {
            return true; // enforcer not enabled
        }
        // Note: Same path definition as HttpRequestFactory.getUri()
        return whiteListedPaths.contains(servletRequest.getRequestURI());
    }

    private List<String> getWhitelistedPathsForPort(int localPort) {
        if (portToWhitelistedPathsMapping.containsKey(0) && portToWhitelistedPathsMapping.size() == 1) {
            return portToWhitelistedPathsMapping.get(0); // for unit tests which uses 0 for listen port
        }
        return portToWhitelistedPathsMapping.get(localPort);
    }

    private boolean isClientAuthenticated(HttpServletRequest servletRequest) {
        return servletRequest.getAttribute(ServletRequest.SERVLET_REQUEST_X509CERT) != null;
    }
}
