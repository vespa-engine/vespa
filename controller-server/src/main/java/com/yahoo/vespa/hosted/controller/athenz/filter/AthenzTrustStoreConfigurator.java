// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.athenz.filter;

import com.google.inject.Inject;
import com.yahoo.jdisc.http.ssl.SslTrustStoreConfigurator;
import com.yahoo.jdisc.http.ssl.SslTrustStoreContext;
import com.yahoo.security.KeyStoreBuilder;
import com.yahoo.security.KeyStoreType;
import com.yahoo.vespa.hosted.controller.athenz.config.AthenzConfig;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;

/**
 * Load trust store with Athenz CA certificates
 *
 * @author bjorncs
 */
public class AthenzTrustStoreConfigurator implements SslTrustStoreConfigurator {

    private final KeyStore trustStore;

    @Inject
    public AthenzTrustStoreConfigurator(AthenzConfig config) {
        this.trustStore = createTrustStore(Paths.get(config.athenzCaTrustStore()));
    }

    private static KeyStore createTrustStore(Path trustStoreFile) {
        return KeyStoreBuilder.withType(KeyStoreType.JKS)
                .fromFile(trustStoreFile, "changeit".toCharArray())
                .build();
    }

    @Override
    public void configure(SslTrustStoreContext context) {
        context.updateTrustStore(trustStore);
    }
}
