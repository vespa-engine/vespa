// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.athenz.instanceproviderservice;

import com.google.inject.Inject;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.jdisc.http.ssl.SslTrustStoreConfigurator;
import com.yahoo.jdisc.http.ssl.SslTrustStoreContext;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.athenz.tls.KeyStoreBuilder;
import com.yahoo.vespa.athenz.tls.KeyStoreType;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.config.AthenzProviderServiceConfig;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.Provider;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.logging.Logger;

/**
 * @author bjorncs
 */
public class AthenzSslTrustStoreConfigurator implements SslTrustStoreConfigurator {

    private static final Logger log = Logger.getLogger(AthenzSslTrustStoreConfigurator.class.getName());
    private static final String CERTIFICATE_ALIAS = "cfgselfsigned";

    private static final Provider provider = new BouncyCastleProvider();
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

    private static X509Certificate createSelfSignedCertificate(KeyPair keyPair, ConfigserverConfig config)
            throws IOException, CertificateException, OperatorCreationException {
        ContentSigner contentSigner = new JcaContentSignerBuilder("SHA256WithRSA").build(keyPair.getPrivate());
        X500Name x500Name = new X500Name("CN="+ config.loadBalancerAddress());
        Instant now = Instant.now();
        Date notBefore = Date.from(now);
        Date notAfter = Date.from(now.plus(Duration.ofDays(30)));

        GeneralNames generalNames = new GeneralNames(
                config.zookeeperserver().stream()
                        .map(server -> new GeneralName(GeneralName.dNSName, server.hostname()))
                        .toArray(GeneralName[]::new));

        X509v3CertificateBuilder certificateBuilder =
                new JcaX509v3CertificateBuilder(
                        x500Name, BigInteger.valueOf(now.toEpochMilli()), notBefore, notAfter, x500Name, keyPair.getPublic()
                )
                        .addExtension(Extension.basicConstraints, true, new BasicConstraints(true))
                        .addExtension(Extension.subjectAlternativeName, false, generalNames);

        return new JcaX509CertificateConverter()
                .setProvider(provider)
                .getCertificate(certificateBuilder.build(contentSigner));
    }

}
