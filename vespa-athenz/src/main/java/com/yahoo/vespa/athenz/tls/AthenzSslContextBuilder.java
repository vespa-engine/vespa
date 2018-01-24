// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.tls;

import com.yahoo.vespa.athenz.api.AthenzIdentityCertificate;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.Certificate;

/**
 * @author bjorncs
 */
public class AthenzSslContextBuilder {

    private KeyStoreSupplier trustStoreSupplier;
    private KeyStoreSupplier keyStoreSupplier;
    private char[] keyStorePassword;

    public AthenzSslContextBuilder() {}

    public AthenzSslContextBuilder withTrustStore(File file, String trustStoreType) {
        this.trustStoreSupplier = () -> loadKeyStoreFromFile(file, null, trustStoreType);
        return this;
    }

    public AthenzSslContextBuilder withTrustStore(KeyStore trustStore) {
        this.trustStoreSupplier = () -> trustStore;
        return this;
    }

    public AthenzSslContextBuilder withIdentityCertificate(AthenzIdentityCertificate certificate) {
        char[] pwd = new char[0];
        this.keyStoreSupplier = () -> {
            KeyStore keyStore = KeyStore.getInstance("JKS");
            keyStore.load(null);
            keyStore.setKeyEntry(
                    "athenz-identity", certificate.getPrivateKey(), pwd, new Certificate[]{certificate.getCertificate()});
            return keyStore;
        };
        this.keyStorePassword = pwd;
        return this;
    }

    public AthenzSslContextBuilder withKeyStore(KeyStore keyStore, char[] password) {
        this.keyStoreSupplier = () -> keyStore;
        this.keyStorePassword = password;
        return this;
    }

    public AthenzSslContextBuilder withKeyStore(File file, char[] password, String keyStoreType) {
        this.keyStoreSupplier = () -> loadKeyStoreFromFile(file, password, keyStoreType);
        this.keyStorePassword = password;
        return this;
    }

    public SSLContext build() {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
            TrustManager[] trustManagers =
                    trustStoreSupplier != null ? createTrustManagers(trustStoreSupplier) : null;
            KeyManager[] keyManagers =
                    keyStoreSupplier != null ? createKeyManagers(keyStoreSupplier, keyStorePassword) : null;
            sslContext.init(keyManagers, trustManagers, null);
            return sslContext;
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static TrustManager[] createTrustManagers(KeyStoreSupplier trustStoreSupplier)
            throws GeneralSecurityException, IOException {
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(trustStoreSupplier.get());
        return trustManagerFactory.getTrustManagers();
    }

    private static KeyManager[] createKeyManagers(KeyStoreSupplier keyStoreSupplier, char[] password)
            throws GeneralSecurityException, IOException {
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStoreSupplier.get(), password);
        return keyManagerFactory.getKeyManagers();
    }

    private static KeyStore loadKeyStoreFromFile(File file, char[] password, String keyStoreType)
            throws IOException, GeneralSecurityException{
        KeyStore keyStore = KeyStore.getInstance(keyStoreType);
        try (FileInputStream in = new FileInputStream(file)) {
            keyStore.load(in, password);
        }
        return keyStore;
    }

    private interface KeyStoreSupplier {
        KeyStore get() throws IOException, GeneralSecurityException;
    }

}
