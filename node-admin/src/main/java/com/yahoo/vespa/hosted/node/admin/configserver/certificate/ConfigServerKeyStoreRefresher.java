// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver.certificate;

import com.yahoo.log.LogLevel;
import com.yahoo.net.HostName;
import com.yahoo.vespa.athenz.tls.KeyAlgorithm;
import com.yahoo.vespa.athenz.tls.KeyStoreBuilder;
import com.yahoo.vespa.athenz.tls.KeyUtils;
import com.yahoo.vespa.athenz.tls.Pkcs10Csr;
import com.yahoo.vespa.athenz.tls.Pkcs10CsrBuilder;
import com.yahoo.vespa.athenz.tls.SignatureAlgorithm;
import com.yahoo.vespa.hosted.node.admin.configserver.ConfigServerApi;
import com.yahoo.vespa.hosted.node.admin.util.KeyStoreOptions;

import javax.security.auth.x500.X500Principal;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Automatically refreshes the KeyStore used to authenticate this node to the configserver.
 * The keystore contains a single certificate signed by one of the configservers.
 *
 * @author freva
 */
public class ConfigServerKeyStoreRefresher {

    private static final Logger logger = Logger.getLogger(ConfigServerKeyStoreRefresher.class.getName());
    private static final String KEY_STORE_ALIAS = "alias";
    static final long MINIMUM_SECONDS_BETWEEN_REFRESH_RETRY = 3600;
    static final SignatureAlgorithm SIGNER_ALGORITHM = SignatureAlgorithm.SHA256_WITH_RSA;
    static final String CONFIG_SERVER_CERTIFICATE_SIGNING_PATH = "/athenz/v1/provider/sign";

    private final ScheduledExecutorService executor;
    private final KeyStoreOptions keyStoreOptions;
    private final Runnable keyStoreUpdatedCallback;
    private final ConfigServerApi configServerApi;
    private final Clock clock;
    private final String hostname;

    public ConfigServerKeyStoreRefresher(
            KeyStoreOptions keyStoreOptions, Runnable keyStoreUpdatedCallback, ConfigServerApi configServerApi) {
        this(keyStoreOptions, keyStoreUpdatedCallback, configServerApi, Executors.newScheduledThreadPool(1),
                Clock.systemUTC(), HostName.getLocalhost());
    }

    ConfigServerKeyStoreRefresher(KeyStoreOptions keyStoreOptions,
                                  Runnable keyStoreUpdatedCallback,
                                  ConfigServerApi configServerApi,
                                  ScheduledExecutorService executor,
                                  Clock clock,
                                  String hostname) {
        this.keyStoreOptions = keyStoreOptions;
        this.keyStoreUpdatedCallback = keyStoreUpdatedCallback;
        this.configServerApi = configServerApi;
        this.executor = executor;
        this.clock = clock;
        this.hostname = hostname;
    }

    public void start() {
        executor.schedule(this::refresh, getSecondsUntilNextRefresh(), TimeUnit.SECONDS);
    }

    void refresh() {
        try {
            if (refreshKeyStoreIfNeeded()) {
                keyStoreUpdatedCallback.run();
            }
            final long secondsUntilNextRefresh = getSecondsUntilNextRefresh();
            executor.schedule(this::refresh, secondsUntilNextRefresh, TimeUnit.SECONDS);
            logger.log(Level.INFO, "Successfully updated keystore, scheduled next refresh in " +
                    secondsUntilNextRefresh + "sec");
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to update keystore on schedule, will try again in " +
                    MINIMUM_SECONDS_BETWEEN_REFRESH_RETRY + "sec", e);
            executor.schedule(this::refresh, MINIMUM_SECONDS_BETWEEN_REFRESH_RETRY, TimeUnit.SECONDS);
        }
    }

    public void stop() {
        executor.shutdownNow();
        do {
            try {
                executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
            } catch (InterruptedException e1) {
                logger.info("Interrupted while waiting for ConfigServerKeyStoreRefresher thread to shutdown");
            }
        } while (!executor.isTerminated());
    }

    public boolean refreshKeyStoreIfNeeded() {
        if (!shouldRefreshCertificate()) return false;

        KeyPair keyPair = generateKeyPair();
        Pkcs10Csr csr = generateCsr(keyPair, hostname);
        X509Certificate certificate = sendCsr(csr);

        storeCertificate(keyPair, certificate);

        logger.log(LogLevel.INFO, "Key store certificate refreshed, expires " +
                certificate.getNotAfter().toInstant());

        return true;
    }

    private long getSecondsUntilNextRefresh() {
        long secondsUntilNextCheck = 0;
        try {
            secondsUntilNextCheck = getSecondsUntilCertificateShouldBeRefreshed();
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to get remaining certificate lifetime", e);
        }

        return Math.max(MINIMUM_SECONDS_BETWEEN_REFRESH_RETRY, secondsUntilNextCheck);
    }

    private boolean shouldRefreshCertificate() {
        try {
            return getSecondsUntilCertificateShouldBeRefreshed() <= 0;
        } catch (Exception e) { // We can't read the key store for whatever reason, let's just try to refresh it
            return true;
        }
    }

    /**
     * Returns number of seconds until we should start trying to refresh the certificate, this should be
     * well before the certificate actually expires so that we have enough time to retry without
     * overloading config server.
     */
    private long getSecondsUntilCertificateShouldBeRefreshed() throws KeyStoreException{
        X509Certificate cert = getConfigServerCertificate();
        long notBefore = cert.getNotBefore().getTime() / 1000;
        long notAfter = cert.getNotAfter().getTime() / 1000;
        long now = clock.millis() / 1000;
        long thirdOfLifetime = (notAfter - notBefore) / 3;

        return Math.max(0, notBefore + thirdOfLifetime - now);
    }

    X509Certificate getConfigServerCertificate() throws KeyStoreException {
        return (X509Certificate) keyStoreOptions.loadKeyStore().getCertificate(KEY_STORE_ALIAS);
    }

    private void storeCertificate(KeyPair keyPair, X509Certificate certificate) {
        keyStoreOptions.path.getParent().toFile().mkdirs();

        KeyStore keyStore = KeyStoreBuilder.withType(keyStoreOptions.keyStoreType)
                .withKeyEntry(KEY_STORE_ALIAS, keyPair.getPrivate(), keyStoreOptions.password, certificate)
                .build();

        keyStoreOptions.storeKeyStore(keyStore);
    }

    private X509Certificate sendCsr(Pkcs10Csr csr) {
        CertificateSerializedPayload certificateSerializedPayload = configServerApi.post(
                CONFIG_SERVER_CERTIFICATE_SIGNING_PATH,
                new CsrSerializedPayload(csr),
                CertificateSerializedPayload.class);

        return certificateSerializedPayload.certificate;
    }

    static KeyPair generateKeyPair() {
        return KeyUtils.generateKeypair(KeyAlgorithm.RSA, 2048);
    }

    private static Pkcs10Csr generateCsr(KeyPair keyPair, String commonName) {
        return Pkcs10CsrBuilder.fromKeypair(new X500Principal("CN=" + commonName), keyPair, SIGNER_ALGORITHM)
                .build();
    }
}
