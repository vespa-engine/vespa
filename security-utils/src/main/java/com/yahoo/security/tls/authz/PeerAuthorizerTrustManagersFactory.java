// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security.tls.authz;

import com.yahoo.security.SslContextBuilder;
import com.yahoo.security.tls.AuthorizationMode;
import com.yahoo.security.tls.policy.AuthorizedPeers;

import java.security.KeyStore;

/**
 * @author bjorncs
 */
public class PeerAuthorizerTrustManagersFactory implements SslContextBuilder.TrustManagerFactory {
    private final AuthorizedPeers authorizedPeers;
    private AuthorizationMode mode;

    public PeerAuthorizerTrustManagersFactory(AuthorizedPeers authorizedPeers, AuthorizationMode mode) {
        this.authorizedPeers = authorizedPeers;
        this.mode = mode;
    }

    @Override
    public PeerAuthorizerTrustManager createTrustManager(KeyStore truststore) {
        return new PeerAuthorizerTrustManager(authorizedPeers, mode, truststore);
    }
}
