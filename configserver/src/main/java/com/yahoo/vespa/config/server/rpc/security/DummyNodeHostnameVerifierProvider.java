// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.rpc.security;

import com.yahoo.component.annotation.Inject;
import com.yahoo.config.provision.security.NodeHostnameVerifier;
import com.yahoo.container.di.componentgraph.Provider;

import javax.net.ssl.SSLSession;

/**
 * @author bjorncs
 */
public class DummyNodeHostnameVerifierProvider implements Provider<NodeHostnameVerifier> {

    private final ThrowingNodeHostnameVerifier instance = new ThrowingNodeHostnameVerifier();

    @Inject public DummyNodeHostnameVerifierProvider() {}

    @Override public NodeHostnameVerifier get() { return instance; }

    @Override public void deconstruct() {}

    private static class ThrowingNodeHostnameVerifier implements NodeHostnameVerifier {
        @Override
        public boolean verify(String hostname, SSLSession session) {
            throw new UnsupportedOperationException();
        }
    }
}
