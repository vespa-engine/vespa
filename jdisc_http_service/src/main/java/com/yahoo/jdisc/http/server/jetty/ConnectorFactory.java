// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.google.inject.Inject;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.http.ConnectorConfig;
import com.yahoo.jdisc.http.ConnectorConfig.Ssl;
import com.yahoo.jdisc.http.ConnectorConfig.Ssl.PemKeyStore;
import com.yahoo.jdisc.http.SecretStore;
import com.yahoo.jdisc.http.ssl.pem.PemSslKeyStore;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.ServerSocketChannel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.util.logging.Logger;

import static com.yahoo.jdisc.http.ConnectorConfig.Ssl.KeyStoreType.Enum.JKS;
import static com.yahoo.jdisc.http.ConnectorConfig.Ssl.KeyStoreType.Enum.PEM;

/**
 * @author Einar M R Rosenvinge
 * @author bjorncs
 */
public class ConnectorFactory {

    private final static Logger log = Logger.getLogger(ConnectorFactory.class.getName());
    private final ConnectorConfig connectorConfig;
    private final SecretStore secretStore;

    @Inject
    public ConnectorFactory(ConnectorConfig connectorConfig, SecretStore secretStore) {
        this.connectorConfig = connectorConfig;
        this.secretStore = secretStore;

        if (connectorConfig.ssl().enabled())
            validateSslConfig(connectorConfig);
    }

    // TODO: can be removed when we have dedicated SSL config in services.xml
    private static void validateSslConfig(ConnectorConfig config) {
        ConnectorConfig.Ssl ssl = config.ssl();

        if (ssl.keyStoreType() == JKS) {
            if (!ssl.pemKeyStore().keyPath().isEmpty() || ! ssl.pemKeyStore().certificatePath().isEmpty()) {
                throw new IllegalArgumentException("pemKeyStore attributes can not be set when keyStoreType is JKS.");
            }
            if (ssl.keyDbKey().isEmpty()) {
                throw new IllegalArgumentException("Missing password for JKS keystore");
            }
        }
        if (ssl.keyStoreType() == PEM) {
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
        if (!ssl.trustStorePath().isEmpty() && ssl.useTrustStorePassword() && ssl.keyDbKey().isEmpty()) {
            throw new IllegalArgumentException("Missing password for JKS truststore");
        }
    }

    public ConnectorConfig getConnectorConfig() {
        return connectorConfig;
    }

    public ServerConnector createConnector(final Metric metric, final Server server, final ServerSocketChannel ch) {
        ServerConnector connector;
        if (connectorConfig.ssl().enabled()) {
            connector = new JDiscServerConnector(connectorConfig, metric, server, ch,
                                                 newSslConnectionFactory(),
                                                 newHttpConnectionFactory());
        } else {
            connector = new JDiscServerConnector(connectorConfig, metric, server, ch,
                                                 newHttpConnectionFactory());
        }
        connector.setPort(connectorConfig.listenPort());
        connector.setName(connectorConfig.name());
        connector.setAcceptQueueSize(connectorConfig.acceptQueueSize());
        connector.setReuseAddress(connectorConfig.reuseAddress());
        double soLingerTimeSeconds = connectorConfig.soLingerTime();
        if (soLingerTimeSeconds == -1) {
            connector.setSoLingerTime(-1);
        } else {
            connector.setSoLingerTime((int)(soLingerTimeSeconds * 1000.0));
        }
        connector.setIdleTimeout((long)(connectorConfig.idleTimeout() * 1000.0));
        connector.setStopTimeout((long)(connectorConfig.stopTimeout() * 1000.0));
        return connector;
    }

    private HttpConnectionFactory newHttpConnectionFactory() {
        HttpConfiguration httpConfig = new HttpConfiguration();
        httpConfig.setSendDateHeader(true);
        httpConfig.setSendServerVersion(false);
        httpConfig.setSendXPoweredBy(false);
        httpConfig.setHeaderCacheSize(connectorConfig.headerCacheSize());
        httpConfig.setOutputBufferSize(connectorConfig.outputBufferSize());
        httpConfig.setRequestHeaderSize(connectorConfig.requestHeaderSize());
        httpConfig.setResponseHeaderSize(connectorConfig.responseHeaderSize());
        if (connectorConfig.ssl().enabled()) {
            httpConfig.addCustomizer(new SecureRequestCustomizer());
        }
        return new HttpConnectionFactory(httpConfig);
    }

    //TODO: does not support loading non-yahoo readable JKS key stores.
    private SslConnectionFactory newSslConnectionFactory() {
        Ssl sslConfig = connectorConfig.ssl();

        SslContextFactory factory = new SslContextFactory();
        switch (sslConfig.clientAuth()) {
            case NEED_AUTH:
                factory.setNeedClientAuth(true);
                break;
            case WANT_AUTH:
                factory.setWantClientAuth(true);
                break;
        }

        if (!sslConfig.prng().isEmpty()) {
            factory.setSecureRandomAlgorithm(sslConfig.prng());
        }

        if (!sslConfig.excludeProtocol().isEmpty()) {
            String[] prots = new String[sslConfig.excludeProtocol().size()];
            for (int i = 0; i < prots.length; i++) {
                prots[i] = sslConfig.excludeProtocol(i).name();
            }
            factory.setExcludeProtocols(prots);
        }
        if (!sslConfig.includeProtocol().isEmpty()) {
            String[] prots = new String[sslConfig.includeProtocol().size()];
            for (int i = 0; i < prots.length; i++) {
                prots[i] = sslConfig.includeProtocol(i).name();
            }
            factory.setIncludeProtocols(prots);
        }
        if (!sslConfig.excludeCipherSuite().isEmpty()) {
            String[] ciphs = new String[sslConfig.excludeCipherSuite().size()];
            for (int i = 0; i < ciphs.length; i++) {
                ciphs[i] = sslConfig.excludeCipherSuite(i).name();
            }
            factory.setExcludeCipherSuites(ciphs);

        }
        if (!sslConfig.includeCipherSuite().isEmpty()) {
            String[] ciphs = new String[sslConfig.includeCipherSuite().size()];
            for (int i = 0; i < ciphs.length; i++) {
                ciphs[i] = sslConfig.includeCipherSuite(i).name();
            }
            factory.setIncludeCipherSuites(ciphs);
        }

        String keyDbPassword = sslConfig.keyDbKey();
        switch (sslConfig.keyStoreType()) {
            case PEM:
                factory.setKeyStore(createPemKeyStore(sslConfig.pemKeyStore()));
                break;
            case JKS:
                factory.setKeyStorePath(sslConfig.keyStorePath());
                factory.setKeyStoreType(sslConfig.keyStoreType().toString());
                factory.setKeyStorePassword(secretStore.getSecret(keyDbPassword));
                break;
        }

        if (!sslConfig.trustStorePath().isEmpty()) {
            factory.setTrustStorePath(sslConfig.trustStorePath());
            factory.setTrustStoreType(sslConfig.trustStoreType().toString());      
            if (sslConfig.useTrustStorePassword()) {
                factory.setTrustStorePassword(secretStore.getSecret(keyDbPassword));
            }
        }

        factory.setKeyManagerFactoryAlgorithm(sslConfig.sslKeyManagerFactoryAlgorithm());
        factory.setProtocol(sslConfig.protocol());
        return new SslConnectionFactory(factory, HttpVersion.HTTP_1_1.asString());
    }

    private static KeyStore createPemKeyStore(PemKeyStore pemKeyStore) {
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
