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
 * A filter that identifies the remote node based on the subject and subject alternative names in client certificate.
 * A {@link NodePrincipal} object is assigned to user principal field if identification is successful.
 *
 * @author bjorncs
 */
@Provides("NodeIdentifierFilter")
public class NodeIdentifierFilter extends JsonSecurityRequestFilterBase {

    private static final Logger log = Logger.getLogger(NodeIdentifierFilter.class.getName());

    private final NodeIdentifier nodeIdentifier;

    @Inject
    public NodeIdentifierFilter(Zone zone, NodeRepository nodeRepository) {
        this.nodeIdentifier = new NodeIdentifier(zone, nodeRepository);
    }

    @Override
    protected Optional<ErrorResponse> filter(DiscFilterRequest request) {
        List<X509Certificate> clientCertificateChain = request.getClientCertificateChain();
        if (clientCertificateChain.isEmpty())
            return Optional.of(new ErrorResponse(Response.Status.UNAUTHORIZED, 0, "Missing client certificate"));
        try {
            NodePrincipal identity = nodeIdentifier.resolveNode(clientCertificateChain);
            request.setUserPrincipal(identity);
            return Optional.empty();
        } catch (NodeIdentifier.NodeIdentifierException e) {
            log.log(LogLevel.WARNING, "Node identification failed: " + e.getMessage(), e);
            return Optional.of(new ErrorResponse(Response.Status.UNAUTHORIZED, 1, e.getMessage()));
        }
    }
}
