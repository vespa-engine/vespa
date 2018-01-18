// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.athenz.impl;

import com.google.inject.Inject;
import com.yahoo.vespa.athenz.tls.AthenzSslContextBuilder;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.AthenzClientFactory;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.AthenzSslContextProvider;
import com.yahoo.vespa.hosted.controller.athenz.config.AthenzConfig;

import javax.net.ssl.SSLContext;
import java.io.File;

/**
 * @author bjorncs
 */
public class AthenzSslContextProviderImpl implements AthenzSslContextProvider {

    private final AthenzClientFactory clientFactory;
    private final AthenzConfig config;

    @Inject
    public AthenzSslContextProviderImpl(AthenzClientFactory clientFactory, AthenzConfig config) {
        this.clientFactory = clientFactory;
        this.config = config;
    }

    @Override
    public SSLContext get() {
        return new AthenzSslContextBuilder()
                .withTrustStore(new File(config.athenzCaTrustStore()), "JKS")
                .withIdentityCertificate(clientFactory.createZtsClientWithServicePrincipal().getIdentityCertificate())
                .build();
    }
}
