// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver.certificate;

import com.yahoo.log.LogLevel;
import com.yahoo.net.HostName;
import com.yahoo.vespa.hosted.node.admin.configserver.ConfigServerApi;
import com.yahoo.vespa.hosted.node.admin.util.KeyStoreOptions;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;

import java.io.FileOutputStream;
import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.cert.CertificateException;
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
    static final String SIGNER_ALGORITHM = "SHA256withRSA";
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

    public boolean refreshKeyStoreIfNeeded() throws
            IOException, NoSuchAlgorithmException, OperatorCreationException, CertificateException, KeyStoreException, NoSuchProviderException {
        if (!shouldRefreshCertificate()) return false;

        KeyPair keyPair = generateKeyPair();
        PKCS10CertificationRequest csr = generateCsr(keyPair, hostname);
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
    private long getSecondsUntilCertificateShouldBeRefreshed()
            throws NoSuchAlgorithmException, CertificateException, NoSuchProviderException, KeyStoreException, IOException {
        X509Certificate cert = getConfigServerCertificate();
        long notBefore = cert.getNotBefore().getTime() / 1000;
        long notAfter = cert.getNotAfter().getTime() / 1000;
        long now = clock.millis() / 1000;
        long thirdOfLifetime = (notAfter - notBefore) / 3;

        return Math.max(0, notBefore + thirdOfLifetime - now);
    }

    X509Certificate getConfigServerCertificate() throws NoSuchAlgorithmException, CertificateException, NoSuchProviderException, KeyStoreException, IOException {
        return (X509Certificate) keyStoreOptions.loadKeyStore().getCertificate(KEY_STORE_ALIAS);
    }

    private void storeCertificate(KeyPair keyPair, X509Certificate certificate)
            throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException, NoSuchProviderException {
        keyStoreOptions.path.getParent().toFile().mkdirs();
        X509Certificate[] certificateChain = {certificate};

        try (FileOutputStream fos = new FileOutputStream(keyStoreOptions.path.toFile())) {
            KeyStore keyStore = keyStoreOptions.getKeyStoreInstance();
            keyStore.load(null, null);
            keyStore.setKeyEntry(KEY_STORE_ALIAS, keyPair.getPrivate(), keyStoreOptions.password, certificateChain);
            keyStore.store(fos, keyStoreOptions.password);
        }
    }

    private X509Certificate sendCsr(PKCS10CertificationRequest csr) {
        CertificateSerializedPayload certificateSerializedPayload = configServerApi.post(
                CONFIG_SERVER_CERTIFICATE_SIGNING_PATH,
                new CsrSerializedPayload(csr),
                CertificateSerializedPayload.class);

        return certificateSerializedPayload.certificate;
    }

    static KeyPair generateKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator rsa = KeyPairGenerator.getInstance("RSA");
        rsa.initialize(2048);
        return rsa.genKeyPair();
    }

    private static PKCS10CertificationRequest generateCsr(KeyPair keyPair, String commonName)
            throws NoSuchAlgorithmException, OperatorCreationException {
        ContentSigner signer = new JcaContentSignerBuilder(SIGNER_ALGORITHM).build(keyPair.getPrivate());

        return new JcaPKCS10CertificationRequestBuilder(new X500Name("CN=" + commonName), keyPair.getPublic())
                .build(signer);
    }
}
