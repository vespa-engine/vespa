package com.yahoo.vespa.hosted.controller.athenz.impl;

import com.google.inject.Inject;
import com.yahoo.container.jdisc.athenz.AthenzIdentityProvider;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.AthenzSslContextProvider;

import javax.net.ssl.SSLContext;

public class SiaAthenzSslContextProvider implements AthenzSslContextProvider {

    private final AthenzIdentityProvider identityProvider;

    @Inject
    public SiaAthenzSslContextProvider(AthenzIdentityProvider identityProvider) {
        this.identityProvider = identityProvider;
    }

    @Override
    public SSLContext get() {
        return identityProvider.getIdentitySslContext();
    }
}
