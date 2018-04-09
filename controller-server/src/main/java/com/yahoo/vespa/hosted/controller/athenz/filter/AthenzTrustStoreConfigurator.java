// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.athenz.filter;

import com.google.inject.Inject;
import com.yahoo.jdisc.http.ssl.SslTrustStoreConfigurator;
import com.yahoo.jdisc.http.ssl.SslTrustStoreContext;
import com.yahoo.vespa.athenz.tls.KeyStoreBuilder;
import com.yahoo.vespa.athenz.tls.KeyStoreType;
import com.yahoo.vespa.hosted.controller.athenz.config.AthenzConfig;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

/**
 * Load trust store with Athenz CA certificates
 *
 * @author bjorncs
 */
public class AthenzTrustStoreConfigurator implements SslTrustStoreConfigurator {

    private final KeyStore trustStore;

    @Inject
    public AthenzTrustStoreConfigurator(AthenzConfig config) {
        this.trustStore = createTrustStore(new File(config.athenzCaTrustStore()));
    }

    private static KeyStore createTrustStore(File trustStoreFile) {
        return KeyStoreBuilder.withType(KeyStoreType.JKS)
                .fromFile(trustStoreFile, "changeit".toCharArray())
                .build();
    }

    @Override
    public void configure(SslTrustStoreContext context) {
        context.updateTrustStore(trustStore);
    }
}
