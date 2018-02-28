// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.athenz.instanceproviderservice;

import com.google.inject.Inject;
import com.yahoo.component.AbstractComponent;
import com.yahoo.config.provision.Zone;
import com.yahoo.container.jdisc.athenz.AthenzIdentityProvider;
import com.yahoo.jdisc.http.ssl.SslKeyStoreConfigurator;
import com.yahoo.jdisc.http.ssl.SslKeyStoreContext;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.config.AthenzProviderServiceConfig;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.impl.AthenzCertificateClient;

import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import static com.yahoo.vespa.hosted.athenz.instanceproviderservice.impl.Utils.getZoneConfig;

/**
 * @author bjorncs
 */
// TODO Cache certificate on disk
@SuppressWarnings("unused") // Component injected into Jetty connector factory
public class AthenzSslKeyStoreConfigurator extends AbstractComponent implements SslKeyStoreConfigurator {
    private static final Logger log = Logger.getLogger(AthenzSslKeyStoreConfigurator.class.getName());
    private static final String CERTIFICATE_ALIAS = "athenz";
    private static final String CERTIFICATE_PASSWORD = "athenz";

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final AthenzCertificateClient certificateClient;
    private final KeyProvider keyProvider;
    private final AthenzProviderServiceConfig.Zones zoneConfig;
    private final AtomicBoolean alreadyConfigured = new AtomicBoolean();
    private final Duration updatePeriod;
    private volatile KeyStore currentKeyStore;

    @Inject
    public AthenzSslKeyStoreConfigurator(AthenzIdentityProvider bootstrapIdentity,
                                         KeyProvider keyProvider,
                                         AthenzProviderServiceConfig config,
                                         Zone zone) {
        AthenzProviderServiceConfig.Zones zoneConfig = getZoneConfig(config, zone);
        this.certificateClient = new AthenzCertificateClient(bootstrapIdentity, config, zoneConfig);
        this.keyProvider = keyProvider;
        this.zoneConfig = zoneConfig;
        this.currentKeyStore = downloadCertificate(keyProvider, certificateClient, zoneConfig);
        this.updatePeriod = Duration.ofDays(config.updatePeriodDays());
    }

    @Override
    public void configure(SslKeyStoreContext sslKeyStoreContext) {
        if (alreadyConfigured.getAndSet(true)) { // For debugging purpose of SslKeyStoreConfigurator interface
            throw new IllegalStateException("Already configured. configure() can only be called once.");
        }
        sslKeyStoreContext.updateKeyStore(currentKeyStore, CERTIFICATE_PASSWORD);
        scheduler.scheduleAtFixedRate(new AthenzCertificateUpdater(sslKeyStoreContext),
                                      updatePeriod.toDays()/*initial delay*/,
                                      updatePeriod.toDays(),
                                      TimeUnit.DAYS);
    }

    @Override
    public void deconstruct() {
        try {
            scheduler.shutdownNow();
            scheduler.awaitTermination(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException("Failed to shutdown Athenz certificate updater on time", e);
        }
    }

    Instant getKeyStoreExpiry() throws KeyStoreException {
        X509Certificate certificate = (X509Certificate) currentKeyStore.getCertificate(CERTIFICATE_ALIAS);
        return certificate.getNotAfter().toInstant();
    }


    private static KeyStore downloadCertificate(KeyProvider keyProvider,
                                                AthenzCertificateClient certificateClient,
                                                AthenzProviderServiceConfig.Zones zoneConfig) {
        try {
            PrivateKey privateKey = keyProvider.getPrivateKey(zoneConfig.secretVersion());
            X509Certificate certificate = certificateClient.updateCertificate(privateKey);
            Instant expirationTime = certificate.getNotAfter().toInstant();
            Duration expiry = Duration.between(certificate.getNotBefore().toInstant(), expirationTime);
            log.log(LogLevel.INFO, String.format("Got Athenz x509 certificate with expiry %s (expires %s)", expiry, expirationTime));
            KeyStore keyStore = KeyStore.getInstance("JKS");
            keyStore.load(null);
            keyStore.setKeyEntry(
                    CERTIFICATE_ALIAS, privateKey, CERTIFICATE_PASSWORD.toCharArray(), new Certificate[]{certificate});
            return keyStore;
        } catch (IOException | NoSuchAlgorithmException | CertificateException | KeyStoreException e) {
            throw new RuntimeException(e);
        }
    }

    private class AthenzCertificateUpdater implements Runnable {

        private final SslKeyStoreContext sslKeyStoreContext;

        AthenzCertificateUpdater(SslKeyStoreContext sslKeyStoreContext) {
            this.sslKeyStoreContext = sslKeyStoreContext;
        }

        @Override
        public void run() {
            try {
                log.log(LogLevel.INFO, "Updating Athenz certificate from ZTS");
                currentKeyStore = downloadCertificate(keyProvider, certificateClient, zoneConfig);
                sslKeyStoreContext.updateKeyStore(currentKeyStore, CERTIFICATE_PASSWORD);
                log.log(LogLevel.INFO, "Athenz certificate reload successfully completed");
            } catch (Throwable e) {
                log.log(LogLevel.ERROR, "Failed to update certificate from ZTS: " + e.getMessage(), e);
            }
        }

    }
}
