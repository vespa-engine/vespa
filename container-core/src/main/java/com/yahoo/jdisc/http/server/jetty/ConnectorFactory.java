// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.google.inject.Inject;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.http.ConnectorConfig;
import com.yahoo.jdisc.http.ssl.SslContextFactoryProvider;
import com.yahoo.security.tls.MixedMode;
import com.yahoo.security.tls.TransportSecurityUtils;
import org.eclipse.jetty.server.ConnectionFactory;
import org.eclipse.jetty.server.DetectorConnectionFactory;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.ProxyConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import java.util.List;

/**
 * @author Einar M R Rosenvinge
 * @author bjorncs
 */
public class ConnectorFactory {

    private final ConnectorConfig connectorConfig;
    private final SslContextFactoryProvider sslContextFactoryProvider;

    @Inject
    public ConnectorFactory(ConnectorConfig connectorConfig,
                            SslContextFactoryProvider sslContextFactoryProvider) {
        runtimeConnectorConfigValidation(connectorConfig);
        this.connectorConfig = connectorConfig;
        this.sslContextFactoryProvider = sslContextFactoryProvider;
    }

    // Perform extra connector config validation that can only be performed at runtime,
    // e.g. due to TLS configuration through environment variables.
    private static void runtimeConnectorConfigValidation(ConnectorConfig config) {
        validateProxyProtocolConfiguration(config);
        validateSecureRedirectConfig(config);
    }

    private static void validateProxyProtocolConfiguration(ConnectorConfig config) {
        ConnectorConfig.ProxyProtocol proxyProtocolConfig = config.proxyProtocol();
        if (proxyProtocolConfig.enabled()) {
            boolean tlsMixedModeEnabled = TransportSecurityUtils.getInsecureMixedMode() != MixedMode.DISABLED;
            if (!isSslEffectivelyEnabled(config) || tlsMixedModeEnabled) {
                throw new IllegalArgumentException("Proxy protocol can only be enabled if connector is effectively HTTPS only");
            }
        }
    }

    private static void validateSecureRedirectConfig(ConnectorConfig config) {
        if (config.secureRedirect().enabled() && isSslEffectivelyEnabled(config)) {
            throw new IllegalArgumentException("Secure redirect can only be enabled on connectors without HTTPS");
        }
    }

    public ConnectorConfig getConnectorConfig() {
        return connectorConfig;
    }

    public ServerConnector createConnector(final Metric metric, final Server server, JettyConnectionLogger connectionLogger) {
        ServerConnector connector = new JDiscServerConnector(
                connectorConfig, metric, server, connectionLogger, createConnectionFactories(metric).toArray(ConnectionFactory[]::new));
        connector.setPort(connectorConfig.listenPort());
        connector.setName(connectorConfig.name());
        connector.setAcceptQueueSize(connectorConfig.acceptQueueSize());
        connector.setReuseAddress(connectorConfig.reuseAddress());
        connector.setIdleTimeout((long)(connectorConfig.idleTimeout() * 1000.0));
        return connector;
    }

    private List<ConnectionFactory> createConnectionFactories(Metric metric) {
        HttpConnectionFactory httpFactory = newHttpConnectionFactory();
        if (!isSslEffectivelyEnabled(connectorConfig)) {
            return List.of(httpFactory);
        } else if (connectorConfig.ssl().enabled()) {
            return connectionFactoriesForHttps(metric, httpFactory);
        } else if (TransportSecurityUtils.isTransportSecurityEnabled()) {
            switch (TransportSecurityUtils.getInsecureMixedMode()) {
                case TLS_CLIENT_MIXED_SERVER:
                case PLAINTEXT_CLIENT_MIXED_SERVER:
                    return List.of(new DetectorConnectionFactory(newSslConnectionFactory(metric, httpFactory)), httpFactory);
                case DISABLED:
                    return connectionFactoriesForHttps(metric, httpFactory);
                default:
                    throw new IllegalStateException();
            }
        } else {
            return List.of(httpFactory);
        }
    }

    private List<ConnectionFactory> connectionFactoriesForHttps(Metric metric, HttpConnectionFactory httpFactory) {
        ConnectorConfig.ProxyProtocol proxyProtocolConfig = connectorConfig.proxyProtocol();
        SslConnectionFactory sslFactory = newSslConnectionFactory(metric, httpFactory);
        if (proxyProtocolConfig.enabled()) {
            if (proxyProtocolConfig.mixedMode()) {
                return List.of(new DetectorConnectionFactory(sslFactory, new ProxyConnectionFactory(sslFactory.getProtocol())), sslFactory, httpFactory);
            } else {
                return List.of(new ProxyConnectionFactory(sslFactory.getProtocol()), sslFactory, httpFactory);
            }
        } else {
            return List.of(sslFactory, httpFactory);
        }
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
        if (isSslEffectivelyEnabled(connectorConfig)) {
            httpConfig.addCustomizer(new SecureRequestCustomizer());
        }
        return new HttpConnectionFactory(httpConfig);
    }

    private SslConnectionFactory newSslConnectionFactory(Metric metric, HttpConnectionFactory httpFactory) {
        SslContextFactory ctxFactory = sslContextFactoryProvider.getInstance(connectorConfig.name(), connectorConfig.listenPort());
        SslConnectionFactory connectionFactory = new SslConnectionFactory(ctxFactory, httpFactory.getProtocol());
        connectionFactory.addBean(new SslHandshakeFailedListener(metric, connectorConfig.name(), connectorConfig.listenPort()));
        return connectionFactory;
    }

    private static boolean isSslEffectivelyEnabled(ConnectorConfig config) {
        return config.ssl().enabled()
                || (config.implicitTlsEnabled() && TransportSecurityUtils.isTransportSecurityEnabled());
    }

}
