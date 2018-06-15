// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.athenz.instanceproviderservice;

import com.google.inject.Inject;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.component.AbstractComponent;
import com.yahoo.config.provision.Zone;
import com.yahoo.jdisc.http.ssl.SslKeyStoreConfigurator;
import com.yahoo.jdisc.http.ssl.SslKeyStoreContext;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.athenz.api.AthenzService;
import com.yahoo.vespa.athenz.client.zts.DefaultZtsClient;
import com.yahoo.vespa.athenz.client.zts.Identity;
import com.yahoo.vespa.athenz.client.zts.ZtsClient;
import com.yahoo.vespa.athenz.identity.ServiceIdentityProvider;
import com.yahoo.vespa.athenz.tls.KeyStoreBuilder;
import com.yahoo.vespa.athenz.tls.KeyStoreType;
import com.yahoo.vespa.athenz.tls.KeyUtils;
import com.yahoo.vespa.athenz.tls.X509CertificateUtils;
import com.yahoo.vespa.athenz.utils.SiaUtils;
import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.config.AthenzProviderServiceConfig;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static com.yahoo.vespa.hosted.athenz.instanceproviderservice.impl.Utils.getZoneConfig;

/**
 * A component that is responsible for retrieving an Athenz TLS certificate and configuring the configserver to use
 * that certificate for its HTTPS endpoint.
 *
 * @author bjorncs
 */
@SuppressWarnings("unused") // Component injected into Jetty connector factory
public class AthenzSslKeyStoreConfigurator extends AbstractComponent implements SslKeyStoreConfigurator {
    private static final Logger log = Logger.getLogger(AthenzSslKeyStoreConfigurator.class.getName());
    private static final String CERTIFICATE_ALIAS = "athenz";
    private static final Duration EXPIRATION_MARGIN = Duration.ofHours(6);
    private static final Path VESPA_SIA_DIRECTORY = Paths.get(Defaults.getDefaults().underVespaHome("var/vespa/sia"));
    private static final Path CA_CERT_FILE = VESPA_SIA_DIRECTORY.resolve("ca-certs.pem");

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final ZtsClient ztsClient;
    private final KeyProvider keyProvider;
    private final AthenzProviderServiceConfig.Zones zoneConfig;
    private final Duration updatePeriod;
    private final AthenzService configserverIdentity;
    private volatile KeyStoreAndPassword currentKeyStore;

    @Inject
    public AthenzSslKeyStoreConfigurator(ServiceIdentityProvider bootstrapIdentity,
                                         KeyProvider keyProvider,
                                         AthenzProviderServiceConfig config,
                                         Zone zone,
                                         ConfigserverConfig configserverConfig) {
        AthenzProviderServiceConfig.Zones zoneConfig = getZoneConfig(config, zone);
        AthenzService configserverIdentity = new AthenzService(zoneConfig.domain(), zoneConfig.serviceName());
        Duration updatePeriod = Duration.ofDays(config.updatePeriodDays());
        DefaultZtsClient ztsClient = new DefaultZtsClient(URI.create(zoneConfig.ztsUrl()).resolve("/zts/v1"), bootstrapIdentity); // TODO Remove URI.resolve() once config in hosted is updated
        this.ztsClient = ztsClient;
        this.keyProvider = keyProvider;
        this.zoneConfig = zoneConfig;
        this.currentKeyStore = initializeKeystore(configserverIdentity, keyProvider, ztsClient, zoneConfig, updatePeriod);
        this.updatePeriod = updatePeriod;
        this.configserverIdentity = configserverIdentity;
    }

    private static KeyStoreAndPassword initializeKeystore(AthenzService configserverIdentity,
                                                          KeyProvider keyProvider,
                                                          ZtsClient ztsClient,
                                                          AthenzProviderServiceConfig.Zones keystoreCacheDirectory,
                                                          Duration updatePeriod) {
        return tryReadKeystoreFile(configserverIdentity, updatePeriod)
                .orElseGet(() -> downloadCertificate(configserverIdentity, keyProvider, ztsClient, keystoreCacheDirectory));
    }

    private static Optional<KeyStoreAndPassword> tryReadKeystoreFile(AthenzService configserverIdentity,
                                                                     Duration updatePeriod) {
        try {
            Optional<X509Certificate> certificate = SiaUtils.readCertificateFile(VESPA_SIA_DIRECTORY, configserverIdentity);
            if (!certificate.isPresent()) return Optional.empty();
            Optional<PrivateKey> privateKey = SiaUtils.readPrivateKeyFile(VESPA_SIA_DIRECTORY, configserverIdentity);
            if (!privateKey.isPresent()) return Optional.empty();
            Instant minimumExpiration = Instant.now().plus(updatePeriod).plus(EXPIRATION_MARGIN);
            boolean isExpired = certificate.get().getNotAfter().toInstant().isBefore(minimumExpiration);
            if (isExpired) return Optional.empty();
            if (Files.notExists(CA_CERT_FILE)) return Optional.empty();
            List<X509Certificate> caCertificates = X509CertificateUtils.certificateListFromPem(new String(Files.readAllBytes(CA_CERT_FILE)));

            List<X509Certificate> chain = new ArrayList<>();
            chain.add(certificate.get());
            chain.addAll(caCertificates);

            char[] password = generateKeystorePassword();
            KeyStore keyStore = KeyStoreBuilder.withType(KeyStoreType.JKS)
                    .withKeyEntry(CERTIFICATE_ALIAS, privateKey.get(), password, chain)
                    .build();
            return Optional.of(new KeyStoreAndPassword(keyStore, password));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void configure(SslKeyStoreContext sslKeyStoreContext) {
        sslKeyStoreContext.updateKeyStore(currentKeyStore.keyStore, new String(currentKeyStore.password));
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
            ztsClient.close();
        } catch (InterruptedException e) {
            throw new RuntimeException("Failed to shutdown Athenz certificate updater on time", e);
        }
    }

    Instant getCertificateExpiry() throws KeyStoreException {
        return getCertificateExpiry(currentKeyStore.keyStore);
    }

    private static Instant getCertificateExpiry(KeyStore keyStore) throws KeyStoreException {
        X509Certificate certificate = (X509Certificate) keyStore.getCertificate(CERTIFICATE_ALIAS);
        return certificate.getNotAfter().toInstant();
    }

    private static KeyStoreAndPassword downloadCertificate(AthenzService configserverIdentity,
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
        writeCredentials(configserverIdentity, certificate, serviceIdentity.caCertificates(), privateKey);
        Instant expirationTime = certificate.getNotAfter().toInstant();
        Duration expiry = Duration.between(certificate.getNotBefore().toInstant(), expirationTime);
        log.log(LogLevel.INFO, String.format("Got Athenz x509 certificate with expiry %s (expires %s)", expiry, expirationTime));

        List<X509Certificate> chain = new ArrayList<>();
        chain.add(certificate);
        chain.addAll(serviceIdentity.caCertificates());
        char[] keystorePassword = generateKeystorePassword();
        KeyStore keyStore = KeyStoreBuilder.withType(KeyStoreType.JKS)
                .withKeyEntry(CERTIFICATE_ALIAS, privateKey, keystorePassword, chain)
                .build();
        return new KeyStoreAndPassword(keyStore, keystorePassword);
    }

    private static void writeCredentials(AthenzService configserverIdentity,
                                         X509Certificate certificate,
                                         List<X509Certificate> caCertificates,
                                         PrivateKey privateKey) {
        SiaUtils.writeCertificateFile(VESPA_SIA_DIRECTORY, configserverIdentity, certificate);
        SiaUtils.writePrivateKeyFile(VESPA_SIA_DIRECTORY, configserverIdentity, privateKey);
        try {
            Files.write(CA_CERT_FILE, X509CertificateUtils.toPem(caCertificates).getBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static char[] generateKeystorePassword() {
        return UUID.randomUUID().toString().toCharArray();
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
                currentKeyStore = downloadCertificate(configserverIdentity, keyProvider, ztsClient, zoneConfig);
                sslKeyStoreContext.updateKeyStore(currentKeyStore.keyStore, new String(currentKeyStore.password));
                log.log(LogLevel.INFO, "Athenz certificate reload successfully completed");
            } catch (Throwable e) {
                log.log(LogLevel.ERROR, "Failed to update certificate from ZTS: " + e.getMessage(), e);
            }
        }

    }

    private static class KeyStoreAndPassword {
        final KeyStore keyStore;
        final char[] password;

        KeyStoreAndPassword(KeyStore keyStore, char[] password) {
            this.keyStore = keyStore;
            this.password = password;
        }
    }
}
