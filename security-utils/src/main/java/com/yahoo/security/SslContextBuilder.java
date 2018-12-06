// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.List;

import static java.util.Collections.singletonList;

/**
 * @author bjorncs
 */
public class SslContextBuilder {

    private KeyStoreSupplier trustStoreSupplier;
    private KeyStoreSupplier keyStoreSupplier;
    private char[] keyStorePassword;
    private TrustManagersFactory trustManagersFactory = SslContextBuilder::createDefaultTrustManagers;

    public SslContextBuilder() {}

    public SslContextBuilder withTrustStore(Path file, KeyStoreType trustStoreType) {
        this.trustStoreSupplier = () -> KeyStoreBuilder.withType(trustStoreType).fromFile(file).build();
        return this;
    }

    public SslContextBuilder withTrustStore(KeyStore trustStore) {
        this.trustStoreSupplier = () -> trustStore;
        return this;
    }

    public SslContextBuilder withTrustStore(X509Certificate caCertificate) {
        return withTrustStore(singletonList(caCertificate));
    }

    public SslContextBuilder withTrustStore(List<X509Certificate> caCertificates) {
        this.trustStoreSupplier = () -> createTrustStore(caCertificates);
        return this;
    }

    public SslContextBuilder withTrustStore(Path pemEncodedCaCertificates) {
        this.trustStoreSupplier = () -> {
            List<X509Certificate> caCertificates =
                    X509CertificateUtils.certificateListFromPem(new String(Files.readAllBytes(pemEncodedCaCertificates)));
            return createTrustStore(caCertificates);
        };
        return this;
    }

    public SslContextBuilder withKeyStore(PrivateKey privateKey, X509Certificate certificate) {
        return withKeyStore(privateKey, singletonList(certificate));
    }

    public SslContextBuilder withKeyStore(PrivateKey privateKey, List<X509Certificate> certificates) {
        char[] pwd = new char[0];
        this.keyStoreSupplier = () -> KeyStoreBuilder.withType(KeyStoreType.JKS).withKeyEntry("default", privateKey, certificates).build();
        this.keyStorePassword = pwd;
        return this;
    }

    public SslContextBuilder withKeyStore(KeyStore keyStore, char[] password) {
        this.keyStoreSupplier = () -> keyStore;
        this.keyStorePassword = password;
        return this;
    }

    public SslContextBuilder withKeyStore(Path file, char[] password, KeyStoreType keyStoreType) {
        this.keyStoreSupplier = () -> KeyStoreBuilder.withType(keyStoreType).fromFile(file, password).build();
        this.keyStorePassword = password;
        return this;
    }

    public SslContextBuilder withKeyStore(Path privateKeyPemFile, Path certificatesPemFile) {
        this.keyStoreSupplier =
                () ->  {
                    PrivateKey privateKey = KeyUtils.fromPemEncodedPrivateKey(new String(Files.readAllBytes(privateKeyPemFile)));
                    List<X509Certificate> certificates = X509CertificateUtils.certificateListFromPem(new String(Files.readAllBytes(certificatesPemFile)));
                    return KeyStoreBuilder.withType(KeyStoreType.JKS)
                            .withKeyEntry("default", privateKey, certificates)
                            .build();
                };
        this.keyStorePassword = new char[0];
        return this;
    }

    public SslContextBuilder withTrustManagerFactory(TrustManagersFactory trustManagersFactory) {
        this.trustManagersFactory = trustManagersFactory;
        return this;
    }

    public SSLContext build() {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
            TrustManager[] trustManagers =
                    trustStoreSupplier != null ? createTrustManagers(trustManagersFactory, trustStoreSupplier) : null;
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

    private static TrustManager[] createTrustManagers(TrustManagersFactory trustManagersFactory, KeyStoreSupplier trustStoreSupplier)
            throws GeneralSecurityException, IOException {
        KeyStore truststore = trustStoreSupplier.get();
        return trustManagersFactory.createTrustManagers(truststore);
    }

    private static TrustManager[] createDefaultTrustManagers(KeyStore truststore) throws GeneralSecurityException {
        TrustManagerFactory trustManagerFactory =
                TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(truststore);
        return trustManagerFactory.getTrustManagers();
    }

    private static KeyManager[] createKeyManagers(KeyStoreSupplier keyStoreSupplier, char[] password)
            throws GeneralSecurityException, IOException {
        KeyManagerFactory keyManagerFactory =
                KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStoreSupplier.get(), password);
        return keyManagerFactory.getKeyManagers();
    }

    private static KeyStore createTrustStore(List<X509Certificate> caCertificates) {
        KeyStoreBuilder trustStoreBuilder = KeyStoreBuilder.withType(KeyStoreType.JKS);
        for (int i = 0; i < caCertificates.size(); i++) {
            trustStoreBuilder.withCertificateEntry("cert-" + i, caCertificates.get(i));
        }
        return trustStoreBuilder.build();
    }

    private interface KeyStoreSupplier {
        KeyStore get() throws IOException, GeneralSecurityException;
    }

    /**
     * A factory interface that is similar to {@link TrustManagerFactory}, but is an interface instead of a class.
     */
    @FunctionalInterface
    public interface TrustManagersFactory {
        TrustManager[] createTrustManagers(KeyStore truststore) throws GeneralSecurityException;
    }

}
