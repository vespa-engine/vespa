// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi.v2.filter;

import java.security.Principal;
import java.security.cert.X509Certificate;
import java.util.List;

/**
 * @author bjorncs
 */
public class NodePrincipal implements Principal {
    private final String hostIdentity;
    private final List<X509Certificate> clientCertificateChain;

    public NodePrincipal(String hostIdentity, List<X509Certificate> clientCertificateChain) {
        this.hostIdentity = hostIdentity;
        this.clientCertificateChain = clientCertificateChain;
    }

    public String getHostIdentityName() {
        return hostIdentity;
    }

    public List<X509Certificate> getClientCertificateChain() {
        return clientCertificateChain;
    }

    @Override
    public String getName() {
        return hostIdentity;
    }

}
