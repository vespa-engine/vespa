// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.ssl;

import com.yahoo.config.InnerNode;
import com.yahoo.jdisc.http.ConnectorConfig;
import com.yahoo.jdisc.http.ssl.pem.PemSslKeyStore;
import com.yahoo.security.KeyStoreBuilder;
import com.yahoo.security.KeyStoreType;
import com.yahoo.security.KeyUtils;
import com.yahoo.security.X509CertificateUtils;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * JDisc's default implementation of {@link SslContextFactoryProvider} that uses the {@link ConnectorConfig} to construct a {@link SslContextFactory}.
 *
 * @author bjorncs
 */
public class DefaultSslContextFactoryProvider implements SslContextFactoryProvider {

    private static final Logger log = Logger.getLogger(DefaultSslContextFactoryProvider.class.getName());

    private final ConnectorConfig connectorConfig;
    @SuppressWarnings("deprecation")
    private final com.yahoo.jdisc.http.SecretStore secretStore;

    public DefaultSslContextFactoryProvider(ConnectorConfig connectorConfig,
                                            @SuppressWarnings("deprecation") com.yahoo.jdisc.http.SecretStore secretStore) {
        validateConfig(connectorConfig.ssl());
        this.connectorConfig = connectorConfig;
        this.secretStore = secretStore;
    }

    @Override
    public SslContextFactory getInstance(String containerId, int port) {
        ConnectorConfig.Ssl sslConfig = connectorConfig.ssl();
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

        // Check if using new ssl syntax from services.xml
        if (!sslConfig.privateKeyFile().isEmpty()) {
            factory.setKeyStore(createKeystore(sslConfig));
            if (!sslConfig.caCertificateFile().isEmpty()) {
                factory.setTrustStore(createTruststore(sslConfig));
            }
            factory.setProtocol("TLS");
        } else { // TODO Vespa 7: Remove support for deprecated ssl connector config
            configureUsingDeprecatedConnectorConfig(sslConfig, factory);
        }
        return factory;
    }

    private void configureUsingDeprecatedConnectorConfig(ConnectorConfig.Ssl sslConfig, SslContextFactory factory) {
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
    }

    private static void validateConfig(ConnectorConfig.Ssl config) {
        if (!config.enabled()) return;
        if (!config.privateKeyFile().isEmpty()) {
            if (config.certificateFile().isEmpty()) {
                throw new IllegalArgumentException("Missing certificate file.");
            }
        } else {
            validateConfigUsingDeprecatedConnectorConfig(config);
        }
    }

    private static void validateConfigUsingDeprecatedConnectorConfig(ConnectorConfig.Ssl config) {
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
