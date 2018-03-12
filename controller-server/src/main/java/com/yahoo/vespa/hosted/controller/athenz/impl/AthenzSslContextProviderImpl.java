// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.athenz.impl;

import com.google.inject.Inject;
import com.yahoo.vespa.athenz.tls.AthenzSslContextBuilder;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.AthenzClientFactory;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.AthenzSslContextProvider;
import com.yahoo.vespa.hosted.controller.athenz.config.AthenzConfig;

import javax.net.ssl.SSLContext;
import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static com.yahoo.vespa.athenz.tls.KeyStoreType.JKS;

/**
 * @author bjorncs
 */
public class AthenzSslContextProviderImpl implements AthenzSslContextProvider {

    private final AthenzClientFactory clientFactory;
    private final AthenzConfig config;
    private final AtomicReference<CachedSslContext> cachedSslContext = new AtomicReference<>();

    @Inject
    public AthenzSslContextProviderImpl(AthenzClientFactory clientFactory, AthenzConfig config) {
        this.clientFactory = clientFactory;
        this.config = config;
    }

    @Override
    public SSLContext get() {
        CachedSslContext currentCachedSslContext = this.cachedSslContext.get();
        if (currentCachedSslContext == null || currentCachedSslContext.isExpired()) {
            SSLContext sslContext = new AthenzSslContextBuilder()
                    .withTrustStore(new File(config.athenzCaTrustStore()), JKS)
                    .withIdentityCertificate(clientFactory.createZtsClientWithServicePrincipal().getIdentityCertificate())
                    .build();
            this.cachedSslContext.set(new CachedSslContext(sslContext));
            return sslContext;
        }
        return currentCachedSslContext.sslContext;
    }

    private static class CachedSslContext {
        // Conservative expiration. Default expiration for Athenz certificates are 30 days.
        static final Duration EXPIRATION = Duration.ofDays(1);

        final SSLContext sslContext;
        final Instant createdAt;

        CachedSslContext(SSLContext sslContext) {
            this.sslContext = sslContext;
            this.createdAt = Instant.now();
        }

        boolean isExpired() {
            return createdAt.plus(EXPIRATION).isAfter(Instant.now());
        }
    }
}
