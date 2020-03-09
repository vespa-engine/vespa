// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.google.inject.Inject;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.http.ConnectorConfig;
import com.yahoo.jdisc.http.ssl.SslContextFactoryProvider;
import com.yahoo.security.tls.TransportSecurityUtils;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.server.ConnectionFactory;
import org.eclipse.jetty.server.DetectorConnectionFactory;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import java.nio.channels.ServerSocketChannel;
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
        this.connectorConfig = connectorConfig;
        this.sslContextFactoryProvider = sslContextFactoryProvider;
    }

    public ConnectorConfig getConnectorConfig() {
        return connectorConfig;
    }

    public ServerConnector createConnector(final Metric metric, final Server server) {
        ServerConnector connector = new JDiscServerConnector(
                connectorConfig, metric, server, createConnectionFactories(metric).toArray(ConnectionFactory[]::new));
        connector.setPort(connectorConfig.listenPort());
        connector.setName(connectorConfig.name());
        connector.setAcceptQueueSize(connectorConfig.acceptQueueSize());
        connector.setReuseAddress(connectorConfig.reuseAddress());
        connector.setIdleTimeout((long)(connectorConfig.idleTimeout() * 1000.0));
        return connector;
    }

    private List<ConnectionFactory> createConnectionFactories(Metric metric) {
        HttpConnectionFactory httpConnectionFactory = newHttpConnectionFactory();
        if (connectorConfig.healthCheckProxy().enable()) {
            return List.of(httpConnectionFactory);
        } else if (connectorConfig.ssl().enabled()) {
            return List.of(newSslConnectionFactory(metric), httpConnectionFactory);
        } else if (TransportSecurityUtils.isTransportSecurityEnabled()) {
            SslConnectionFactory sslConnectionsFactory = newSslConnectionFactory(metric);
            switch (TransportSecurityUtils.getInsecureMixedMode()) {
                case TLS_CLIENT_MIXED_SERVER:
                case PLAINTEXT_CLIENT_MIXED_SERVER:
                    return List.of(new DetectorConnectionFactory(sslConnectionsFactory), httpConnectionFactory);
                case DISABLED:
                    return List.of(sslConnectionsFactory, httpConnectionFactory);
                default:
                    throw new IllegalStateException();
            }
        } else {
            return List.of(httpConnectionFactory);
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
        if (connectorConfig.ssl().enabled() || TransportSecurityUtils.isTransportSecurityEnabled()) { // TODO Cleanup once mixed mode is gone
            httpConfig.addCustomizer(new SecureRequestCustomizer());
        }
        return new HttpConnectionFactory(httpConfig);
    }

    private SslConnectionFactory newSslConnectionFactory(Metric metric) {
        SslContextFactory ctxFactory = sslContextFactoryProvider.getInstance(connectorConfig.name(), connectorConfig.listenPort());
        SslConnectionFactory connectionFactory = new SslConnectionFactory(ctxFactory, HttpVersion.HTTP_1_1.asString());
        connectionFactory.addBean(new SslHandshakeFailedListener(metric, connectorConfig.name(), connectorConfig.listenPort()));
        return connectionFactory;
    }

}
