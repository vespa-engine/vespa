// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.athenz.instanceproviderservice;

import com.google.inject.Inject;
import com.yahoo.component.AbstractComponent;
import com.yahoo.config.provision.Zone;
import com.yahoo.jdisc.http.ssl.SslContextFactoryProvider;
import com.yahoo.log.LogLevel;
import com.yahoo.security.KeyStoreBuilder;
import com.yahoo.security.KeyStoreType;
import com.yahoo.security.KeyUtils;
import com.yahoo.vespa.athenz.api.AthenzService;
import com.yahoo.vespa.athenz.client.zts.DefaultZtsClient;
import com.yahoo.vespa.athenz.client.zts.Identity;
import com.yahoo.vespa.athenz.client.zts.ZtsClient;
import com.yahoo.vespa.athenz.identity.ServiceIdentityProvider;
import com.yahoo.vespa.athenz.utils.SiaUtils;
import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.config.AthenzProviderServiceConfig;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static com.yahoo.vespa.hosted.athenz.instanceproviderservice.impl.Utils.getZoneConfig;

/**
 * Configures the JDisc https connector with the configserver's Athenz provider certificate and private key.
 *
 * @author bjorncs
 */
public class ConfigserverSslContextFactoryProvider extends AbstractComponent implements SslContextFactoryProvider {
    private static final String CERTIFICATE_ALIAS = "athenz";
    private static final Duration EXPIRATION_MARGIN = Duration.ofHours(6);
    private static final Path VESPA_SIA_DIRECTORY = Paths.get(Defaults.getDefaults().underVespaHome("var/vespa/sia"));

    private static final Logger log = Logger.getLogger(ConfigserverSslContextFactoryProvider.class.getName());

    private final SslContextFactory sslContextFactory;
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(runnable -> new Thread(runnable, "configserver-ssl-context-factory-provider"));
    private final ZtsClient ztsClient;
    private final KeyProvider keyProvider;
    private final AthenzProviderServiceConfig.Zones zoneConfig;
    private final AthenzService configserverIdentity;

    @Inject
    public ConfigserverSslContextFactoryProvider(ServiceIdentityProvider bootstrapIdentity,
                                                 KeyProvider keyProvider,
                                                 AthenzProviderServiceConfig config,
                                                 Zone zone) {
        this.zoneConfig = getZoneConfig(config, zone);
        this.ztsClient = new DefaultZtsClient(URI.create(zoneConfig.ztsUrl()), bootstrapIdentity);
        this.keyProvider = keyProvider;
        this.configserverIdentity = new AthenzService(zoneConfig.domain(), zoneConfig.serviceName());

        Duration updatePeriod = Duration.ofDays(config.updatePeriodDays());
        Path trustStoreFile = Paths.get(config.athenzCaTrustStore());
        this.sslContextFactory = initializeSslContextFactory(keyProvider, trustStoreFile, updatePeriod, configserverIdentity, ztsClient, zoneConfig);
        scheduler.scheduleAtFixedRate(new KeystoreUpdater(sslContextFactory),
                                      updatePeriod.toDays()/*initial delay*/,
                                      updatePeriod.toDays(),
                                      TimeUnit.DAYS);
    }

    @Override
    public SslContextFactory getInstance(String containerId, int port) {
        return sslContextFactory;
    }

    Instant getCertificateNotAfter() {
        try {
            X509Certificate certificate = (X509Certificate) sslContextFactory.getKeyStore().getCertificate(CERTIFICATE_ALIAS);
            return certificate.getNotAfter().toInstant();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Unable to find configserver certificate from keystore: " + e.getMessage(), e);
        }
    }

    @Override
    public void deconstruct() {
        try {
            scheduler.shutdownNow();
            scheduler.awaitTermination(30, TimeUnit.SECONDS);
            ztsClient.close();
        } catch (InterruptedException e) {
            throw new RuntimeException("Failed to shutdown Athenz certificate updater on time", e);
        }
    }

    private static SslContextFactory initializeSslContextFactory(KeyProvider keyProvider,
                                                                 Path trustStoreFile,
                                                                 Duration updatePeriod,
                                                                 AthenzService configserverIdentity,
                                                                 ZtsClient ztsClient,
                                                                 AthenzProviderServiceConfig.Zones zoneConfig) {
        SslContextFactory factory = new SslContextFactory();

        factory.setWantClientAuth(true);

        KeyStore trustStore =
                KeyStoreBuilder.withType(KeyStoreType.JKS)
                        .fromFile(trustStoreFile)
                        .build();
        factory.setTrustStore(trustStore);

        KeyStore keyStore =
                tryReadKeystoreFile(configserverIdentity, updatePeriod)
                        .orElseGet(() -> updateKeystore(configserverIdentity, generateKeystorePassword(), keyProvider, ztsClient, zoneConfig));
        factory.setKeyStore(keyStore);
        factory.setKeyStorePassword("");
        factory.setEndpointIdentificationAlgorithm(null); // disable https hostname verification of clients (must be disabled when using Athenz x509 certificates)
        return factory;
    }

    private static Optional<KeyStore> tryReadKeystoreFile(AthenzService configserverIdentity, Duration updatePeriod) {
        Optional<X509Certificate> certificate = SiaUtils.readCertificateFile(VESPA_SIA_DIRECTORY, configserverIdentity);
        if (!certificate.isPresent()) return Optional.empty();
        Optional<PrivateKey> privateKey = SiaUtils.readPrivateKeyFile(VESPA_SIA_DIRECTORY, configserverIdentity);
        if (!privateKey.isPresent()) return Optional.empty();
        Instant minimumExpiration = Instant.now().plus(updatePeriod).plus(EXPIRATION_MARGIN);
        boolean isExpired = certificate.get().getNotAfter().toInstant().isBefore(minimumExpiration);
        if (isExpired) return Optional.empty();
        KeyStore keyStore = KeyStoreBuilder.withType(KeyStoreType.JKS)
                .withKeyEntry(CERTIFICATE_ALIAS, privateKey.get(), certificate.get())
                .build();
        return Optional.of(keyStore);
    }

    private static KeyStore updateKeystore(AthenzService configserverIdentity,
                                           char[] keystorePwd,
                                           KeyProvider keyProvider,
                                           ZtsClient ztsClient,
                                           AthenzProviderServiceConfig.Zones zoneConfig) {
        PrivateKey privateKey = keyProvider.getPrivateKey(zoneConfig.secretVersion());
        PublicKey publicKey = KeyUtils.extractPublicKey(privateKey);
        Identity serviceIdentity = ztsClient.getServiceIdentity(configserverIdentity,
                                                                Integer.toString(zoneConfig.secretVersion()),
                                                                new KeyPair(publicKey, privateKey),
                                                                zoneConfig.certDnsSuffix());
        X509Certificate certificate = serviceIdentity.certificate();
        SiaUtils.writeCertificateFile(VESPA_SIA_DIRECTORY, configserverIdentity, certificate);
        SiaUtils.writePrivateKeyFile(VESPA_SIA_DIRECTORY, configserverIdentity, privateKey);
        Instant expirationTime = certificate.getNotAfter().toInstant();
        Duration expiry = Duration.between(certificate.getNotBefore().toInstant(), expirationTime);
        log.log(LogLevel.INFO, String.format("Got Athenz x509 certificate with expiry %s (expires %s)", expiry, expirationTime));
        return KeyStoreBuilder.withType(KeyStoreType.JKS)
                .withKeyEntry(CERTIFICATE_ALIAS, privateKey, keystorePwd, certificate)
                .build();
    }

    private static char[] generateKeystorePassword() {
        return UUID.randomUUID().toString().toCharArray();
    }

    private class KeystoreUpdater implements Runnable {
        final SslContextFactory sslContextFactory;

        KeystoreUpdater(SslContextFactory sslContextFactory) {
            this.sslContextFactory = sslContextFactory;
        }

        @Override
        public void run() {
            try {
                log.log(LogLevel.INFO, "Updating configserver provider certificate from ZTS");
                char[] keystorePwd = generateKeystorePassword();
                KeyStore keyStore = updateKeystore(configserverIdentity, keystorePwd, keyProvider, ztsClient, zoneConfig);
                sslContextFactory.reload(scf -> {
                    scf.setKeyStore(keyStore);
                    scf.setKeyStorePassword(new String(keystorePwd));
                });
                log.log(LogLevel.INFO, "Certificate successfully updated");
            } catch (Throwable t) {
                log.log(LogLevel.ERROR, "Failed to update certificate from ZTS: " + t.getMessage(), t);
            }
        }
    }
}
