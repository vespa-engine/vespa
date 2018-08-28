// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.athenz.instanceproviderservice;

import com.google.inject.Inject;
import com.yahoo.jdisc.http.ssl.SslTrustStoreConfigurator;
import com.yahoo.jdisc.http.ssl.SslTrustStoreContext;
import com.yahoo.vespa.athenz.tls.KeyStoreBuilder;
import com.yahoo.vespa.athenz.tls.KeyStoreType;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.config.AthenzProviderServiceConfig;

import java.io.File;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.X509Certificate;
import java.time.Instant;

/**
 * Programmatic configuration of configserver's truststore
 *
 * @author bjorncs
 */
public class AthenzSslTrustStoreConfigurator implements SslTrustStoreConfigurator {

    private static final String CERTIFICATE_ALIAS = "cfgselfsigned";

    private final KeyStore trustStore;

    @Inject
    public AthenzSslTrustStoreConfigurator(AthenzProviderServiceConfig athenzProviderServiceConfig) {
        this.trustStore = createTrustStore(athenzProviderServiceConfig);
    }

    @Override
    public void configure(SslTrustStoreContext sslTrustStoreContext) {
        sslTrustStoreContext.updateTrustStore(trustStore);
    }

    Instant getTrustStoreExpiry() throws KeyStoreException {
        X509Certificate certificate = (X509Certificate) trustStore.getCertificate(CERTIFICATE_ALIAS);
        return certificate.getNotAfter().toInstant();
    }

    private static KeyStore createTrustStore(AthenzProviderServiceConfig athenzProviderServiceConfig) {
        try {
            return KeyStoreBuilder.withType(KeyStoreType.JKS)
                    .fromFile(new File(athenzProviderServiceConfig.athenzCaTrustStore()))
                    .build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
