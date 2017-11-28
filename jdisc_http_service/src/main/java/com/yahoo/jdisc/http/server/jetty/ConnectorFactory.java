// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.google.inject.Inject;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.http.ConnectorConfig;
import com.yahoo.jdisc.http.ConnectorConfig.Ssl;
import com.yahoo.jdisc.http.SecretStore;
import com.yahoo.jdisc.http.ssl.DefaultSslKeyStoreContext;
import com.yahoo.jdisc.http.ssl.SslKeyStoreConfigurator;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import java.nio.channels.ServerSocketChannel;

/**
 * @author Einar M R Rosenvinge
 * @author bjorncs
 */
public class ConnectorFactory {

    private final ConnectorConfig connectorConfig;
    private final SecretStore secretStore;
    private final SslKeyStoreConfigurator sslKeyStoreConfigurator;

    @Inject
    public ConnectorFactory(ConnectorConfig connectorConfig,
                            SecretStore secretStore,
                            SslKeyStoreConfigurator sslKeyStoreConfigurator) {
        this.connectorConfig = connectorConfig;
        this.secretStore = secretStore;
        this.sslKeyStoreConfigurator = sslKeyStoreConfigurator;

        if (connectorConfig.ssl().enabled())
            validateSslConfig(connectorConfig);
    }

    // TODO: can be removed when we have dedicated SSL config in services.xml
    private static void validateSslConfig(ConnectorConfig config) {
        ConnectorConfig.Ssl ssl = config.ssl();
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

        sslKeyStoreConfigurator.configure(new DefaultSslKeyStoreContext(factory));

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

}
