// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security.tls.authz;

import com.yahoo.security.SslContextBuilder;
import com.yahoo.security.tls.AuthorizationMode;
import com.yahoo.security.tls.policy.AuthorizedPeers;

import javax.net.ssl.TrustManager;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

/**
 * @author bjorncs
 */
public class PeerAuthorizerTrustManagersFactory implements SslContextBuilder.TrustManagersFactory {
    private final AuthorizedPeers authorizedPeers;
    private AuthorizationMode mode;

    public PeerAuthorizerTrustManagersFactory(AuthorizedPeers authorizedPeers, AuthorizationMode mode) {
        this.authorizedPeers = authorizedPeers;
        this.mode = mode;
    }

    @Override
    public TrustManager[] createTrustManagers(KeyStore truststore) throws GeneralSecurityException {
        return PeerAuthorizerTrustManager.wrapTrustManagersFromKeystore(authorizedPeers, mode, truststore);
    }
}
