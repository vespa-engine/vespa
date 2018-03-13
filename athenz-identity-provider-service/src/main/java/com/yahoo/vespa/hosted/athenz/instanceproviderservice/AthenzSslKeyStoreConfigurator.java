// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.athenz.instanceproviderservice;

import com.google.inject.Inject;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.component.AbstractComponent;
import com.yahoo.config.provision.Zone;
import com.yahoo.container.jdisc.athenz.AthenzIdentityProvider;
import com.yahoo.jdisc.http.ssl.SslKeyStoreConfigurator;
import com.yahoo.jdisc.http.ssl.SslKeyStoreContext;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.athenz.tls.KeyStoreBuilder;
import com.yahoo.vespa.athenz.tls.KeyStoreType;
import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.config.AthenzProviderServiceConfig;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.impl.AthenzCertificateClient;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static com.yahoo.vespa.athenz.tls.KeyStoreUtils.writeKeyStoreToFile;
import static com.yahoo.vespa.hosted.athenz.instanceproviderservice.impl.Utils.getZoneConfig;

/**
 * @author bjorncs
 */
@SuppressWarnings("unused") // Component injected into Jetty connector factory
public class AthenzSslKeyStoreConfigurator extends AbstractComponent implements SslKeyStoreConfigurator {
    private static final Logger log = Logger.getLogger(AthenzSslKeyStoreConfigurator.class.getName());
    private static final String CERTIFICATE_ALIAS = "athenz";
    private static final String CERTIFICATE_PASSWORD = "athenz";
    private static final Duration EXPIRATION_MARGIN = Duration.ofHours(6);

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final AthenzCertificateClient certificateClient;
    private final KeyProvider keyProvider;
    private final AthenzProviderServiceConfig.Zones zoneConfig;
    private final Duration updatePeriod;
    private final Path keystoreCachePath;
    private volatile KeyStore currentKeyStore;

    @Inject
    public AthenzSslKeyStoreConfigurator(AthenzIdentityProvider bootstrapIdentity,
                                         KeyProvider keyProvider,
                                         AthenzProviderServiceConfig config,
                                         Zone zone,
                                         ConfigserverConfig configserverConfig) {
        AthenzProviderServiceConfig.Zones zoneConfig = getZoneConfig(config, zone);
        Path keystoreCachePath = createKeystoreCachePath(configserverConfig);
        AthenzCertificateClient certificateClient = new AthenzCertificateClient(bootstrapIdentity, zoneConfig);
        Duration updatePeriod = Duration.ofDays(config.updatePeriodDays());
        this.certificateClient = certificateClient;
        this.keyProvider = keyProvider;
        this.zoneConfig = zoneConfig;
        this.currentKeyStore = initializeKeystore(keyProvider, certificateClient, zoneConfig, keystoreCachePath, updatePeriod);
        this.updatePeriod = updatePeriod;
        this.keystoreCachePath = keystoreCachePath;
    }

    private static KeyStore initializeKeystore(KeyProvider keyProvider,
                                               AthenzCertificateClient certificateClient,
                                               AthenzProviderServiceConfig.Zones zoneConfig,
                                               Path keystoreCachePath,
                                               Duration updatePeriod) {
        return tryReadKeystoreFile(keystoreCachePath.toFile(), updatePeriod)
                .orElseGet(() -> downloadCertificate(keyProvider, certificateClient, zoneConfig, keystoreCachePath));
    }

    private static Optional<KeyStore> tryReadKeystoreFile(File certificateFile, Duration updatePeriod) {
        try {
            if (!certificateFile.exists()) return Optional.empty();
            KeyStore keyStore = KeyStoreBuilder.withType(KeyStoreType.JKS)
                    .fromFile(certificateFile)
                    .build();
            Instant minimumExpiration = Instant.now().plus(updatePeriod).plus(EXPIRATION_MARGIN);
            boolean isExpired = getCertificateExpiry(keyStore).isBefore(minimumExpiration);
            if (isExpired) return Optional.empty();
            return Optional.of(keyStore);
        } catch (GeneralSecurityException e) {
            log.log(LogLevel.ERROR, "Failed to read keystore from disk: " + e.getMessage(), e);
            return Optional.empty();
        }
    }

    private static Path createKeystoreCachePath(ConfigserverConfig configserverConfig) {
        return Paths.get(
                Defaults.getDefaults().underVespaHome(configserverConfig.configServerDBDir()),
                "server-x509-athenz-cert.jks");
    }

    @Override
    public void configure(SslKeyStoreContext sslKeyStoreContext) {
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

    Instant getCertificateExpiry() throws KeyStoreException {
        return getCertificateExpiry(currentKeyStore);
    }

    private static Instant getCertificateExpiry(KeyStore keyStore) throws KeyStoreException {
        X509Certificate certificate = (X509Certificate) keyStore.getCertificate(CERTIFICATE_ALIAS);
        return certificate.getNotAfter().toInstant();
    }

    private static KeyStore downloadCertificate(KeyProvider keyProvider,
                                                AthenzCertificateClient certificateClient,
                                                AthenzProviderServiceConfig.Zones zoneConfig,
                                                Path keystoreCachePath) {
        PrivateKey privateKey = keyProvider.getPrivateKey(zoneConfig.secretVersion());
        X509Certificate certificate = certificateClient.updateCertificate(privateKey);
        Instant expirationTime = certificate.getNotAfter().toInstant();
        Duration expiry = Duration.between(certificate.getNotBefore().toInstant(), expirationTime);
        log.log(LogLevel.INFO, String.format("Got Athenz x509 certificate with expiry %s (expires %s)", expiry, expirationTime));

        KeyStore keyStore = KeyStoreBuilder.withType(KeyStoreType.JKS)
                .withKeyEntry(CERTIFICATE_ALIAS, privateKey, CERTIFICATE_PASSWORD.toCharArray(), certificate)
                .build();
        tryWriteKeystore(keyStore, keystoreCachePath);
        return keyStore;
    }

    private static void tryWriteKeystore(KeyStore keyStore, Path keystoreCachePath) {
        try  {
            writeKeyStoreToFile(keyStore, keystoreCachePath.toFile());
        } catch (Exception e) {
            log.log(LogLevel.ERROR, "Failed to write keystore to disk: " + e.getMessage(), e);
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
                currentKeyStore = downloadCertificate(keyProvider, certificateClient, zoneConfig, keystoreCachePath);
                sslKeyStoreContext.updateKeyStore(currentKeyStore, CERTIFICATE_PASSWORD);
                log.log(LogLevel.INFO, "Athenz certificate reload successfully completed");
            } catch (Throwable e) {
                log.log(LogLevel.ERROR, "Failed to update certificate from ZTS: " + e.getMessage(), e);
            }
        }

    }
}
