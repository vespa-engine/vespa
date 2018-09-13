// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.ssl;

import com.yahoo.config.InnerNode;
import com.yahoo.jdisc.http.ConnectorConfig;
import com.yahoo.jdisc.http.ssl.pem.PemSslKeyStore;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * A implementation of {@link SslContextFactoryProvider} to be injected into non-ssl connectors or connectors using legacy ssl config
 *
 * @author bjorncs
 */
// TODO Vespa 7: Remove legacy ssl config
public class LegacySslContextFactoryProvider implements SslContextFactoryProvider {
    private static final Logger log = Logger.getLogger(LegacySslContextFactoryProvider.class.getName());

    private final ConnectorConfig connectorConfig;
    @SuppressWarnings("deprecation")
    private final com.yahoo.jdisc.http.SecretStore secretStore;

    public LegacySslContextFactoryProvider(ConnectorConfig connectorConfig,
                                            @SuppressWarnings("deprecation") com.yahoo.jdisc.http.SecretStore secretStore) {
        validateConfig(connectorConfig.ssl());
        this.connectorConfig = connectorConfig;
        this.secretStore = secretStore;
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

        // NOTE: All ciphers matching ^TLS_RSA_.*$ are disabled by default in Jetty 9.4.12+ (https://github.com/eclipse/jetty.project/issues/2807)
        // JDisc will allow these ciphers by default to support older clients (e.g. Java 8u60 and curl 7.29.0)
        // Removing the exclusion will allow for the TLS_RSA variants that are not covered by other exclusions
        String[] excludedCiphersWithoutTlsRsaExclusion = Arrays.stream(factory.getExcludeCipherSuites())
                .filter(cipher -> !cipher.equals("^TLS_RSA_.*$"))
                .toArray(String[]::new);
        factory.setExcludeCipherSuites(excludedCiphersWithoutTlsRsaExclusion);

        switch (sslConfig.keyStoreType()) {
            case JKS:
                factory.setKeyStorePath(sslConfig.keyStorePath());
                factory.setKeyStoreType("JKS");
                factory.setKeyStorePassword(secretStore.getSecret(sslConfig.keyDbKey()));
                break;
            case PEM:
                factory.setKeyStorePath(sslConfig.keyStorePath());
                factory.setKeyStore(createPemKeyStore(sslConfig.pemKeyStore()));
                break;
        }

        if (!sslConfig.trustStorePath().isEmpty()) {
            factory.setTrustStorePath(sslConfig.trustStorePath());
            factory.setTrustStoreType(sslConfig.trustStoreType().toString());
            if (sslConfig.useTrustStorePassword()) {
                factory.setTrustStorePassword(secretStore.getSecret(sslConfig.keyDbKey()));
            }
        }

        if (!sslConfig.prng().isEmpty()) {
            factory.setSecureRandomAlgorithm(sslConfig.prng());
        }

        setStringArrayParameter(
                factory, sslConfig.excludeProtocol(), ConnectorConfig.Ssl.ExcludeProtocol::name, SslContextFactory::setExcludeProtocols);
        setStringArrayParameter(
                factory, sslConfig.includeProtocol(), ConnectorConfig.Ssl.IncludeProtocol::name, SslContextFactory::setIncludeProtocols);
        setStringArrayParameter(
                factory, sslConfig.excludeCipherSuite(), ConnectorConfig.Ssl.ExcludeCipherSuite::name, SslContextFactory::setExcludeCipherSuites);
        setStringArrayParameter(
                factory, sslConfig.includeCipherSuite(), ConnectorConfig.Ssl.IncludeCipherSuite::name, SslContextFactory::setIncludeCipherSuites);

        factory.setKeyManagerFactoryAlgorithm(sslConfig.sslKeyManagerFactoryAlgorithm());
        factory.setProtocol(sslConfig.protocol());

        return factory;
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
        if (!config.trustStorePath().isEmpty() && config.useTrustStorePassword() && config.keyDbKey().isEmpty()) {
            throw new IllegalArgumentException("Missing password for JKS truststore");
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

    private static <T extends InnerNode> void setStringArrayParameter(SslContextFactory sslContextFactory,
                                                                      List<T> configValues,
                                                                      Function<T, String> nameProperty,
                                                                      BiConsumer<SslContextFactory, String[]> setter) {
        if (!configValues.isEmpty()) {
            String[] nameArray = configValues.stream().map(nameProperty).toArray(String[]::new);
            setter.accept(sslContextFactory, nameArray);
        }
    }

}
