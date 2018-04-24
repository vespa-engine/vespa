// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.ssl;

import com.google.inject.Inject;
import com.yahoo.jdisc.http.ConnectorConfig;
import com.yahoo.jdisc.http.ssl.pem.PemSslKeyStore;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.util.logging.Logger;

/**
 * @author bjorncs
 */
public class DefaultSslKeyStoreConfigurator implements SslKeyStoreConfigurator {

    private static final Logger log = Logger.getLogger(DefaultSslKeyStoreConfigurator.class.getName());

    @SuppressWarnings("deprecation")
    private final com.yahoo.jdisc.http.SecretStore secretStore;
    private final ConnectorConfig.Ssl config;

    @Inject
    @SuppressWarnings("deprecation")
    public DefaultSslKeyStoreConfigurator(ConnectorConfig config, com.yahoo.jdisc.http.SecretStore secretStore) {
        validateConfig(config.ssl());
        this.secretStore = secretStore;
        this.config = config.ssl();
    }

    private static void validateConfig(ConnectorConfig.Ssl config) {
        if (!config.enabled()) return;
        switch (config.keyStoreType()) {
            case JKS:
                validateJksConfig(config);
                break;
            case PEM:
                validatePemConfig(config);
                break;
        }
    }

    @Override
    public void configure(SslKeyStoreContext context) {
        if (!config.enabled()) return;
        switch (config.keyStoreType()) {
            case JKS:
                context.updateKeyStore(config.keyStorePath(), "JKS", secretStore.getSecret(config.keyDbKey()));
                break;
            case PEM:
                context.updateKeyStore(createPemKeyStore(config.pemKeyStore()));
                break;
        }
    }

    private static void validateJksConfig(ConnectorConfig.Ssl ssl) {
        if (!ssl.pemKeyStore().keyPath().isEmpty() || ! ssl.pemKeyStore().certificatePath().isEmpty()) {
            throw new IllegalArgumentException("pemKeyStore attributes can not be set when keyStoreType is JKS.");
        }
        if (ssl.keyDbKey().isEmpty()) {
            throw new IllegalArgumentException("Missing password for JKS keystore");
        }
    }

    private static void validatePemConfig(ConnectorConfig.Ssl ssl) {
        if (! ssl.keyStorePath().isEmpty()) {
            throw new IllegalArgumentException("keyStorePath can not be set when keyStoreType is PEM");
        }
        if (!ssl.keyDbKey().isEmpty()) {
            // TODO Make an error once there are separate passwords for truststore and keystore
            log.warning("Encrypted PEM key stores are not supported. Password is only applied to truststore");
        }
        if (ssl.pemKeyStore().certificatePath().isEmpty()) {
            throw new IllegalArgumentException("Missing certificate path.");
        }
        if (ssl.pemKeyStore().keyPath().isEmpty()) {
            throw new IllegalArgumentException("Missing key path.");
        }
    }

    private static KeyStore createPemKeyStore(ConnectorConfig.Ssl.PemKeyStore pemKeyStore) {
        try {
            Path certificatePath = Paths.get(pemKeyStore.certificatePath());
            Path keyPath = Paths.get(pemKeyStore.keyPath());
            return new PemSslKeyStore(certificatePath, keyPath).loadJavaKeyStore();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (Exception e) {
            throw new RuntimeException("Failed setting up key store for " + pemKeyStore.keyPath() + ", " + pemKeyStore.certificatePath(), e);
        }
    }

}
