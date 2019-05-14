// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision.security;

import com.google.inject.Inject;
import com.google.inject.Provider;

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

    private static class ThrowingNodeIdentifier implements NodeIdentifier {
        @Override
        public NodeIdentity identifyNode(List<X509Certificate> peerCertificateChain) {
            throw new UnsupportedOperationException();
        }
    }
}
