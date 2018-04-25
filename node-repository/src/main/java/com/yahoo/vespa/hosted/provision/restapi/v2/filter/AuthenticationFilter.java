// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi.v2.filter;

import com.google.inject.Inject;
import com.yahoo.config.provision.Zone;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.http.filter.DiscFilterRequest;
import com.yahoo.jdisc.http.filter.security.base.JsonSecurityRequestFilterBase;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.yolean.chain.Provides;

import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * A filter that authenticates the remote host based on the subject and subject alternative names in client certificate.
 * A {@link NodePrincipal} object is assigned to user principal field if authentication is successful.
 *
 * @author bjorncs
 */
@Provides("AuthenticationFilter")
public class AuthenticationFilter extends JsonSecurityRequestFilterBase {

    private static final Logger log = Logger.getLogger(AuthenticationFilter.class.getName());

    private final HostAuthenticator authenticator;

    @Inject
    public AuthenticationFilter(Zone zone, NodeRepository nodeRepository) {
        this.authenticator = new HostAuthenticator(zone, nodeRepository);
    }

    @Override
    protected Optional<ErrorResponse> filter(DiscFilterRequest request) {
        List<X509Certificate> clientCertificateChain = request.getClientCertificateChain();
        if (clientCertificateChain.isEmpty())
            return Optional.of(new ErrorResponse(Response.Status.UNAUTHORIZED, 0, "Missing client certificate"));
        try {
            NodePrincipal identity = authenticator.authenticate(clientCertificateChain);
            request.setUserPrincipal(identity);
            return Optional.empty();
        } catch (HostAuthenticator.AuthenticationException e) {
            log.log(LogLevel.WARNING, "Authentication failed: " + e.getMessage(), e);
            return Optional.of(new ErrorResponse(Response.Status.UNAUTHORIZED, 1, e.getMessage()));
        }
    }
}
