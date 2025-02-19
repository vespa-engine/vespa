// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.yahoo.jdisc.http.ConnectorConfig;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;

import java.util.Optional;

/**
 * A Jetty handler that enforces TLS client authentication with configurable white list.
 *
 * @author bjorncs
 */
class TlsClientAuthenticationEnforcer extends Handler.Wrapper {

    private final ConnectorConfig.TlsClientAuthEnforcer cfg;

    TlsClientAuthenticationEnforcer(ConnectorConfig.TlsClientAuthEnforcer cfg, Handler handler) {
        super(handler);
        if (!cfg.enable()) throw new IllegalArgumentException();
        this.cfg = cfg;
    }

    @Override
    public boolean handle(Request request, Response response, Callback callback) throws Exception {
        if (!isRequestToWhitelistedBinding(request) && !hasClientX509Certificate(request)) {
            Response.writeError(request, response, callback, HttpStatus.UNAUTHORIZED_401,
                    "Client did not present a x509 certificate, " +
                    "or presented a certificate not issued by any of the CA certificates in trust store.");
            return true;
        }
        return super.handle(request, response, callback);
    }

    private boolean isRequestToWhitelistedBinding(Request request) {
        return cfg.pathWhitelist().contains(request.getHttpURI().getPath());
    }

    private boolean hasClientX509Certificate(Request request) {
        return Optional.ofNullable(request.getAttribute(EndPoint.SslSessionData.ATTRIBUTE))
                .map(EndPoint.SslSessionData.class::cast)
                .map(d -> d.peerCertificates() != null)
                .orElse(false);
    }
}
