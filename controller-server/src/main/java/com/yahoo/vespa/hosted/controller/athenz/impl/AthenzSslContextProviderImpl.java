// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.athenz.impl;

import com.google.inject.Inject;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.AthenzClientFactory;
import com.yahoo.vespa.athenz.api.AthenzIdentityCertificate;
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

    private final AthenzClientFactory clientFactory;
    private final AthenzConfig config;

    @Inject
    public AthenzSslContextProviderImpl(AthenzClientFactory clientFactory, AthenzConfig config) {
        this.clientFactory = clientFactory;
        this.config = config;
    }

    @Override
    public SSLContext get() {
        return createSslContext();
    }

    private SSLContext createSslContext() {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
            sslContext.init(createKeyManagersWithServiceCertificate(clientFactory.createZtsClientWithServicePrincipal()),
                            createTrustManagersWithAthenzCa(config),
                            null);
            return sslContext;
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new RuntimeException(e);
        }
    }

    private static KeyManager[] createKeyManagersWithServiceCertificate(ZtsClient ztsClient) {
        try {
            AthenzIdentityCertificate identityCertificate = ztsClient.getIdentityCertificate();
            KeyStore keyStore = KeyStore.getInstance("JKS");
            keyStore.load(null);
            keyStore.setKeyEntry("athenz-controller-key",
                                 identityCertificate.getPrivateKey(),
                                 new char[0],
                                 new Certificate[]{identityCertificate.getCertificate()});
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keyStore, new char[0]);
            return keyManagerFactory.getKeyManagers();
        } catch (KeyStoreException | NoSuchAlgorithmException | UnrecoverableKeyException | CertificateException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static TrustManager[] createTrustManagersWithAthenzCa(AthenzConfig config) {
        try {
            KeyStore trustStore = KeyStore.getInstance("JKS");
            try (FileInputStream in = new FileInputStream(config.athenzCaTrustStore())) {
                trustStore.load(in, "changeit".toCharArray());
            }
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(trustStore);
            return trustManagerFactory.getTrustManagers();
        } catch (CertificateException | IOException | KeyStoreException | NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
