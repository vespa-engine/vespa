// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.ssl.impl;

import com.yahoo.jdisc.http.ConnectorConfig;
import com.yahoo.jdisc.http.ssl.SslContextFactoryProvider;
import com.yahoo.security.KeyStoreBuilder;
import com.yahoo.security.KeyStoreType;
import com.yahoo.security.KeyUtils;
import com.yahoo.security.X509CertificateUtils;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;

/**
 * JDisc's default implementation of {@link SslContextFactoryProvider} that uses the {@link ConnectorConfig} to construct a {@link SslContextFactory}.
 *
 * @author bjorncs
 */
public class DefaultSslContextFactoryProvider implements SslContextFactoryProvider {

    private final ConnectorConfig connectorConfig;

    public DefaultSslContextFactoryProvider(ConnectorConfig connectorConfig) {
        validateConfig(connectorConfig.ssl());
        this.connectorConfig = connectorConfig;
    }

    @Override
    public SslContextFactory getInstance(String containerId, int port) {
        ConnectorConfig.Ssl sslConfig = connectorConfig.ssl();
        if (!sslConfig.enabled()) throw new IllegalStateException();
        SslContextFactory factory = new JDiscSslContextFactory();

        switch (sslConfig.clientAuth()) {
            case NEED_AUTH:
                factory.setNeedClientAuth(true);
                break;
            case WANT_AUTH:
                factory.setWantClientAuth(true);
                break;
        }

        // Check if using new ssl syntax from services.xml
        factory.setKeyStore(createKeystore(sslConfig));
        factory.setKeyStorePassword("");
        if (!sslConfig.caCertificateFile().isEmpty()) {
            factory.setTrustStore(createTruststore(sslConfig));
        }
        factory.setProtocol("TLS");
        return factory;
    }

    private static void validateConfig(ConnectorConfig.Ssl config) {
        if (!config.enabled()) return;
        if (config.certificateFile().isEmpty()) {
            throw new IllegalArgumentException("Missing certificate file.");
        }
        if (config.privateKeyFile().isEmpty()) {
            throw new IllegalArgumentException("Missing private key file.");
        }

    }

    private static KeyStore createTruststore(ConnectorConfig.Ssl sslConfig) {
        List<X509Certificate> caCertificates = X509CertificateUtils.certificateListFromPem(readToString(sslConfig.caCertificateFile()));
        KeyStoreBuilder truststoreBuilder = KeyStoreBuilder.withType(KeyStoreType.JKS);
        for (int i = 0; i < caCertificates.size(); i++) {
            truststoreBuilder.withCertificateEntry("entry-" + i, caCertificates.get(i));
        }
        return truststoreBuilder.build();
    }

    private static KeyStore createKeystore(ConnectorConfig.Ssl sslConfig) {
        PrivateKey privateKey = KeyUtils.fromPemEncodedPrivateKey(readToString(sslConfig.privateKeyFile()));
        List<X509Certificate> certificates = X509CertificateUtils.certificateListFromPem(readToString(sslConfig.certificateFile()));
        return KeyStoreBuilder.withType(KeyStoreType.JKS).withKeyEntry("default", privateKey, certificates).build();
    }

    private static String readToString(String filename) {
        try {
            return new String(Files.readAllBytes(Paths.get(filename)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
