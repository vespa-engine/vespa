// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.athenz.instanceproviderservice;

import com.google.inject.Inject;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.jdisc.http.ssl.SslTrustStoreConfigurator;
import com.yahoo.jdisc.http.ssl.SslTrustStoreContext;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.athenz.tls.KeyStoreBuilder;
import com.yahoo.vespa.athenz.tls.KeyStoreType;
import com.yahoo.vespa.athenz.tls.SignatureAlgorithm;
import com.yahoo.vespa.athenz.tls.X509CertificateBuilder;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.config.AthenzProviderServiceConfig;

import javax.security.auth.x500.X500Principal;
import java.io.File;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.logging.Logger;

/**
 * @author bjorncs
 */
public class AthenzSslTrustStoreConfigurator implements SslTrustStoreConfigurator {

    private static final Logger log = Logger.getLogger(AthenzSslTrustStoreConfigurator.class.getName());
    private static final String CERTIFICATE_ALIAS = "cfgselfsigned";

    private final KeyStore trustStore;

    @Inject
    public AthenzSslTrustStoreConfigurator(KeyProvider keyProvider,
                                           ConfigserverConfig configserverConfig,
                                           AthenzProviderServiceConfig athenzProviderServiceConfig) {
        this.trustStore = createTrustStore(keyProvider, configserverConfig, athenzProviderServiceConfig);
    }

    @Override
    public void configure(SslTrustStoreContext sslTrustStoreContext) {
        sslTrustStoreContext.updateTrustStore(trustStore);
        log.log(LogLevel.INFO, "Configured JDisc trust store with self-signed certificate");
    }

    Instant getTrustStoreExpiry() throws KeyStoreException {
        X509Certificate certificate = (X509Certificate) trustStore.getCertificate(CERTIFICATE_ALIAS);
        return certificate.getNotAfter().toInstant();
    }

    private static KeyStore createTrustStore(KeyProvider keyProvider,
                                             ConfigserverConfig configserverConfig,
                                             AthenzProviderServiceConfig athenzProviderServiceConfig) {
        try {
            KeyPair keyPair = getKeyPair(keyProvider, configserverConfig, athenzProviderServiceConfig);
            X509Certificate selfSignedCertificate = createSelfSignedCertificate(keyPair, configserverConfig);
            log.log(LogLevel.FINE, "Generated self-signed certificate: " + selfSignedCertificate);
            return KeyStoreBuilder.withType(KeyStoreType.JKS)
                    .fromFile(new File(athenzProviderServiceConfig.athenzCaTrustStore()), "changeit".toCharArray())
                    .withCertificateEntry(CERTIFICATE_ALIAS, selfSignedCertificate)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static KeyPair getKeyPair(KeyProvider keyProvider,
                                      ConfigserverConfig configserverConfig,
                                      AthenzProviderServiceConfig athenzProviderServiceConfig) {
        String key = configserverConfig.environment() + "." + configserverConfig.region();
        AthenzProviderServiceConfig.Zones zoneConfig = athenzProviderServiceConfig.zones(key);
        return keyProvider.getKeyPair(zoneConfig.secretVersion());
    }

    private static X509Certificate createSelfSignedCertificate(KeyPair keyPair, ConfigserverConfig config)  {
        X500Principal subject = new X500Principal("CN="+ config.loadBalancerAddress());
        Instant now = Instant.now();
        X509CertificateBuilder builder = X509CertificateBuilder
                .fromKeypair(
                        keyPair,
                        subject,
                        now,
                        now.plus(Duration.ofDays(30)),
                        SignatureAlgorithm.SHA256_WITH_RSA,
                        now.toEpochMilli())
                .setBasicConstraints(true, true);
        config.zookeeperserver().forEach(server -> builder.addSubjectAlternativeName(server.hostname()));
        return builder.build();
    }

}
