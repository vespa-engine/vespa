// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.http.ConnectorConfig;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.HandlerWrapper;

import java.io.IOException;

/**
 * A Jetty handler that enforces TLS client authentication with configurable white list.
 *
 * @author bjorncs
 */
class TlsClientAuthenticationEnforcer extends HandlerWrapper {

    private final ConnectorConfig.TlsClientAuthEnforcer cfg;

    TlsClientAuthenticationEnforcer(ConnectorConfig.TlsClientAuthEnforcer cfg) {
        if (!cfg.enable()) throw new IllegalArgumentException();
        this.cfg = cfg;
    }

    @Override
    public void handle(String target, Request request, HttpServletRequest servletRequest, HttpServletResponse servletResponse) throws IOException, ServletException {
        if (isRequest(request)
                && !isRequestToWhitelistedBinding(request)
                && !isClientAuthenticated(servletRequest)) {
            servletResponse.sendError(
                    Response.Status.UNAUTHORIZED,
                    "Client did not present a x509 certificate, " +
                            "or presented a certificate not issued by any of the CA certificates in trust store.");
        } else {
            _handler.handle(target, request, servletRequest, servletResponse);
        }
    }

    private boolean isRequest(Request request) { return request.getDispatcherType() == DispatcherType.REQUEST; }

    private boolean isRequestToWhitelistedBinding(Request jettyRequest) {
        // Note: Same path definition as HttpRequestFactory.getUri()
        return cfg.pathWhitelist().contains(jettyRequest.getRequestURI());
    }

    private boolean isClientAuthenticated(HttpServletRequest servletRequest) {
        return servletRequest.getAttribute(RequestUtils.SERVLET_REQUEST_X509CERT) != null;
    }
}
