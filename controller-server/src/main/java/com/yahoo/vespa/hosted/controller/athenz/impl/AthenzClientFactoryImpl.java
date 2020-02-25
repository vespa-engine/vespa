// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.athenz.impl;

import com.google.inject.Inject;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.api.AthenzService;
import com.yahoo.vespa.athenz.client.zms.DefaultZmsClient;
import com.yahoo.vespa.athenz.client.zms.ZmsClient;
import com.yahoo.vespa.athenz.client.zts.DefaultZtsClient;
import com.yahoo.vespa.athenz.client.zts.ZtsClient;
import com.yahoo.vespa.athenz.identity.ServiceIdentityProvider;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.AthenzClientFactory;
import com.yahoo.vespa.hosted.controller.athenz.config.AthenzConfig;

import java.net.URI;

/**
 * @author bjorncs
 */
public class AthenzClientFactoryImpl implements AthenzClientFactory {

    private final AthenzConfig config;
    private final ServiceIdentityProvider identityProvider;

    @Inject
    public AthenzClientFactoryImpl(ServiceIdentityProvider identityProvider, AthenzConfig config) {
        this.identityProvider = identityProvider;
        this.config = config;
    }

    @Override
    public AthenzIdentity getControllerIdentity() {
        return identityProvider.identity();
    }

    /**
     * @return A ZMS client instance with the service identity as principal.
     */
    @Override
    public ZmsClient createZmsClient() {
        return new DefaultZmsClient(URI.create(config.zmsUrl()), identityProvider);
    }

    /**
     * @return A ZTS client instance with the service identity as principal.
     */
    @Override
    public ZtsClient createZtsClient() {
        return new DefaultZtsClient(URI.create(config.ztsUrl()), identityProvider);
    }

    @Override
    public boolean cacheZtsUserDomains() {
        return true;
    }

}
