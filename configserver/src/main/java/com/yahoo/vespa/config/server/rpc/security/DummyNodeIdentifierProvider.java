// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.rpc.security;

import com.yahoo.component.annotation.Inject;
import com.yahoo.config.provision.security.NodeIdentifier;
import com.yahoo.config.provision.security.NodeIdentity;
import com.yahoo.container.di.componentgraph.Provider;

import java.security.cert.X509Certificate;
import java.util.List;

/**
 * @author bjorncs
 */
public class DummyNodeIdentifierProvider implements Provider<NodeIdentifier> {

    private final ThrowingNodeIdentifier instance = new ThrowingNodeIdentifier();

    @Inject
    public DummyNodeIdentifierProvider() {}

    @Override
    public NodeIdentifier get() {
        return instance;
    }

    @Override
    public void deconstruct() {}

    private static class ThrowingNodeIdentifier implements NodeIdentifier {
        @Override
        public NodeIdentity identifyNode(List<X509Certificate> peerCertificateChain) {
            throw new UnsupportedOperationException();
        }
    }
}
