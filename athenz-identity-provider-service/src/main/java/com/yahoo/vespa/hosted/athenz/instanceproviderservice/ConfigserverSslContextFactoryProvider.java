// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.athenz.instanceproviderservice;

import com.google.inject.Inject;
import com.yahoo.jdisc.http.ssl.impl.TlsContextBasedProvider;
import com.yahoo.security.KeyStoreBuilder;
import com.yahoo.security.KeyStoreType;
import com.yahoo.security.KeyUtils;
import com.yahoo.security.SslContextBuilder;
import com.yahoo.security.tls.DefaultTlsContext;
import com.yahoo.security.tls.MutableX509KeyManager;
import com.yahoo.security.tls.PeerAuthentication;
import com.yahoo.security.tls.TlsContext;
import com.yahoo.vespa.athenz.api.AthenzService;
import com.yahoo.vespa.athenz.client.zts.DefaultZtsClient;
import com.yahoo.vespa.athenz.client.zts.Identity;
import com.yahoo.vespa.athenz.client.zts.ZtsClient;
import com.yahoo.vespa.athenz.identity.ServiceIdentityProvider;
import com.yahoo.vespa.athenz.utils.SiaUtils;
import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.config.AthenzProviderServiceConfig;

import javax.net.ssl.SSLContext;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
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
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Configures the JDisc https connector with the configserver's Athenz provider certificate and private key.
 *
 * @author bjorncs
 */
public class ConfigserverSslContextFactoryProvider extends TlsContextBasedProvider  {

    private static final String CERTIFICATE_ALIAS = "athenz";
    private static final Duration EXPIRATION_MARGIN = Duration.ofHours(6);
    private static final Path VESPA_SIA_DIRECTORY = Paths.get(Defaults.getDefaults().underVespaHome("var/vespa/sia"));

    private static final Logger log = Logger.getLogger(ConfigserverSslContextFactoryProvider.class.getName());

    private final TlsContext tlsContext;
    private final MutableX509KeyManager keyManager = new MutableX509KeyManager();
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(runnable -> new Thread(runnable, "configserver-ssl-context-factory-provider"));
    private final ZtsClient ztsClient;
    private final KeyProvider keyProvider;
    private final AthenzProviderServiceConfig athenzProviderServiceConfig;
    private final AthenzService configserverIdentity;

    @Inject
    public ConfigserverSslContextFactoryProvider(ServiceIdentityProvider bootstrapIdentity,
                                                 KeyProvider keyProvider,
                                                 AthenzProviderServiceConfig config) {
        this.athenzProviderServiceConfig = config;
        this.ztsClient = new DefaultZtsClient.Builder(URI.create(athenzProviderServiceConfig.ztsUrl()))
                .withIdentityProvider(bootstrapIdentity).build();
        this.keyProvider = keyProvider;
        this.configserverIdentity = new AthenzService(athenzProviderServiceConfig.domain(), athenzProviderServiceConfig.serviceName());

        Duration updatePeriod = Duration.ofDays(config.updatePeriodDays());
        Path trustStoreFile = Paths.get(config.athenzCaTrustStore());
        this.tlsContext = createTlsContext(keyProvider, keyManager, trustStoreFile, updatePeriod, configserverIdentity, ztsClient, athenzProviderServiceConfig);
        scheduler.scheduleAtFixedRate(new KeystoreUpdater(keyManager),
                                      updatePeriod.toDays()/*initial delay*/,
                                      updatePeriod.toDays(),
                                      TimeUnit.DAYS);
    }

    @Override
    protected TlsContext getTlsContext(String containerId, int port) {
        return tlsContext;
    }

    Instant getCertificateNotAfter() {
        return keyManager.currentManager().getCertificateChain(CERTIFICATE_ALIAS)[0].getNotAfter().toInstant();
    }

    @Override
    public void deconstruct() {
        try {
            scheduler.shutdownNow();
            scheduler.awaitTermination(30, TimeUnit.SECONDS);
            ztsClient.close();
            super.deconstruct();
        } catch (InterruptedException e) {
            throw new RuntimeException("Failed to shutdown Athenz certificate updater on time", e);
        }
    }

    private static TlsContext createTlsContext(KeyProvider keyProvider,
                                               MutableX509KeyManager keyManager,
                                               Path trustStoreFile,
                                               Duration updatePeriod,
                                               AthenzService configserverIdentity,
                                               ZtsClient ztsClient,
                                               AthenzProviderServiceConfig zoneConfig) {
        KeyStore keyStore =
                tryReadKeystoreFile(configserverIdentity, updatePeriod)
                        .orElseGet(() -> updateKeystore(configserverIdentity, generateKeystorePassword(), keyProvider, ztsClient, zoneConfig));
        keyManager.updateKeystore(keyStore, new char[0]);
        SSLContext sslContext = new SslContextBuilder()
                .withTrustStore(trustStoreFile, KeyStoreType.JKS)
                .withKeyManager(keyManager)
                .build();
        return new DefaultTlsContext(sslContext, PeerAuthentication.WANT);
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
                                           AthenzProviderServiceConfig zoneConfig) {
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
        log.log(Level.INFO, String.format("Got Athenz x509 certificate with expiry %s (expires %s)", expiry, expirationTime));
        return KeyStoreBuilder.withType(KeyStoreType.JKS)
                .withKeyEntry(CERTIFICATE_ALIAS, privateKey, keystorePwd, certificate)
                .build();
    }

    private static char[] generateKeystorePassword() {
        return UUID.randomUUID().toString().toCharArray();
    }

    private class KeystoreUpdater implements Runnable {
        final MutableX509KeyManager keyManager;

        KeystoreUpdater(MutableX509KeyManager keyManager) {
            this.keyManager = keyManager;
        }

        @Override
        public void run() {
            try {
                log.log(Level.INFO, "Updating configserver provider certificate from ZTS");
                char[] keystorePwd = generateKeystorePassword();
                KeyStore keyStore = updateKeystore(configserverIdentity, keystorePwd, keyProvider, ztsClient, athenzProviderServiceConfig);
                keyManager.updateKeystore(keyStore, keystorePwd);
                log.log(Level.INFO, "Certificate successfully updated");
            } catch (Throwable t) {
                log.log(Level.SEVERE, "Failed to update certificate from ZTS: " + t.getMessage(), t);
            }
        }
    }
}
