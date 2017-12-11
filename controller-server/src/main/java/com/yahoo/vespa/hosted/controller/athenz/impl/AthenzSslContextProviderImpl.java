// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.athenz.impl;

import com.google.inject.Inject;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.AthenzIdentityCertificate;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.AthenzSslContextProvider;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.ZtsClient;
import com.yahoo.vespa.hosted.controller.athenz.config.AthenzConfig;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;

/**
 * @author bjorncs
 */
public class AthenzSslContextProviderImpl implements AthenzSslContextProvider {

    private final ZtsClient ztsClient;
    private final AthenzConfig config;

    @Inject
    public AthenzSslContextProviderImpl(ZtsClient ztsClient, AthenzConfig config) {
        this.ztsClient = ztsClient;
        this.config = config;
    }

    @Override
    public SSLContext get() {
        return createSslContext();
    }

    private SSLContext createSslContext() {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
            sslContext.init(createKeyManagersWithServiceCertificate(), createTrustManagersWithAthenzCa(), null);
            return sslContext;
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new RuntimeException(e);
        }
    }

    private KeyManager[] createKeyManagersWithServiceCertificate() {
        try {
            AthenzIdentityCertificate identityCertificate = ztsClient.getIdentityCertificate();
            KeyStore keyStore = KeyStore.getInstance("JKS");
            keyStore.setKeyEntry("athenz-controller-key",
                                 identityCertificate.getPrivateKey(),
                                 new char[0],
                                 new Certificate[]{identityCertificate.getCertificate()});
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance("X509");
            keyManagerFactory.init(keyStore, new char[0]);
            return keyManagerFactory.getKeyManagers();
        } catch (KeyStoreException | NoSuchAlgorithmException | UnrecoverableKeyException e) {
            throw new RuntimeException(e);
        }
    }

    private TrustManager[] createTrustManagersWithAthenzCa() {
        try {
            KeyStore trustStore = KeyStore.getInstance("JKS");
            try (FileInputStream in = new FileInputStream(config.athenzCaTrustStore())) {
                trustStore.load(in, "changeit".toCharArray());
            }
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance("X509");
            trustManagerFactory.init(trustStore);
            return trustManagerFactory.getTrustManagers();
        } catch (CertificateException | IOException | KeyStoreException | NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
