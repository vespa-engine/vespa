// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.ssl;

import com.yahoo.config.InnerNode;
import com.yahoo.jdisc.http.ConnectorConfig;
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
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * JDisc's default implementation of {@link SslContextFactoryProvider} that uses the {@link ConnectorConfig} to construct a {@link SslContextFactory}.
 *
 * @author bjorncs
 */
public class DefaultSslContextFactoryProvider implements SslContextFactoryProvider {

    private final ConnectorConfig connectorConfig;
    private final SslKeyStoreConfigurator sslKeyStoreConfigurator;
    private final SslTrustStoreConfigurator sslTrustStoreConfigurator;

    public DefaultSslContextFactoryProvider(ConnectorConfig connectorConfig,
                                            SslKeyStoreConfigurator sslKeyStoreConfigurator,
                                            SslTrustStoreConfigurator sslTrustStoreConfigurator) {
        this.connectorConfig = connectorConfig;
        this.sslKeyStoreConfigurator = sslKeyStoreConfigurator;
        this.sslTrustStoreConfigurator = sslTrustStoreConfigurator;
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
        } else {
            // TODO Remove SslKeyStoreConfigurator / SslTrustStoreConfigurator
            sslKeyStoreConfigurator.configure(new DefaultSslKeyStoreContext(factory));
            sslTrustStoreConfigurator.configure(new DefaultSslTrustStoreContext(factory));

            // TODO Remove support for deprecated ssl connector config
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
        return factory;
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
