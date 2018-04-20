// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.tls;

import com.yahoo.vespa.athenz.api.AthenzIdentityCertificate;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

/**
 * @author bjorncs
 */
public class SslContextBuilder {

    private KeyStoreSupplier trustStoreSupplier;
    private KeyStoreSupplier keyStoreSupplier;
    private char[] keyStorePassword;

    public SslContextBuilder() {}

    public SslContextBuilder withTrustStore(File file, KeyStoreType trustStoreType) {
        this.trustStoreSupplier = () -> KeyStoreBuilder.withType(trustStoreType).fromFile(file).build();
        return this;
    }

    public SslContextBuilder withTrustStore(KeyStore trustStore) {
        this.trustStoreSupplier = () -> trustStore;
        return this;
    }

    public SslContextBuilder withIdentityCertificate(AthenzIdentityCertificate certificate) {
        return withKeyStore(certificate.getPrivateKey(), certificate.getCertificate());
    }

    public SslContextBuilder withKeyStore(PrivateKey privateKey, X509Certificate certificate) {
        char[] pwd = new char[0];
        this.keyStoreSupplier = () -> KeyStoreBuilder.withType(KeyStoreType.JKS).withKeyEntry("default", privateKey, certificate).build();
        this.keyStorePassword = pwd;
        return this;
    }

    public SslContextBuilder withKeyStore(KeyStore keyStore, char[] password) {
        this.keyStoreSupplier = () -> keyStore;
        this.keyStorePassword = password;
        return this;
    }

    public SslContextBuilder withKeyStore(File file, char[] password, KeyStoreType keyStoreType) {
        this.keyStoreSupplier = () -> KeyStoreBuilder.withType(keyStoreType).fromFile(file, password).build();
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
        TrustManagerFactory trustManagerFactory =
                TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(trustStoreSupplier.get());
        return trustManagerFactory.getTrustManagers();
    }

    private static KeyManager[] createKeyManagers(KeyStoreSupplier keyStoreSupplier, char[] password)
            throws GeneralSecurityException, IOException {
        KeyManagerFactory keyManagerFactory =
                KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStoreSupplier.get(), password);
        return keyManagerFactory.getKeyManagers();
    }

    private interface KeyStoreSupplier {
        KeyStore get() throws IOException, GeneralSecurityException;
    }

}
