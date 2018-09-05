// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.google.inject.Inject;
import com.yahoo.config.InnerNode;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.http.ConnectorConfig;
import com.yahoo.jdisc.http.ConnectorConfig.Ssl;
import com.yahoo.jdisc.http.ConnectorConfig.Ssl.ExcludeCipherSuite;
import com.yahoo.jdisc.http.ConnectorConfig.Ssl.ExcludeProtocol;
import com.yahoo.jdisc.http.ConnectorConfig.Ssl.IncludeCipherSuite;
import com.yahoo.jdisc.http.ConnectorConfig.Ssl.IncludeProtocol;
import com.yahoo.jdisc.http.ssl.DefaultSslKeyStoreContext;
import com.yahoo.jdisc.http.ssl.DefaultSslTrustStoreContext;
import com.yahoo.jdisc.http.ssl.SslKeyStoreConfigurator;
import com.yahoo.jdisc.http.ssl.SslTrustStoreConfigurator;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import java.nio.channels.ServerSocketChannel;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * @author Einar M R Rosenvinge
 * @author bjorncs
 */
public class ConnectorFactory {

    private final ConnectorConfig connectorConfig;
    private final SslKeyStoreConfigurator sslKeyStoreConfigurator;
    private final SslTrustStoreConfigurator sslTrustStoreConfigurator;

    @Inject
    public ConnectorFactory(ConnectorConfig connectorConfig,
                            SslKeyStoreConfigurator sslKeyStoreConfigurator,
                            SslTrustStoreConfigurator sslTrustStoreConfigurator) {
        this.connectorConfig = connectorConfig;
        this.sslKeyStoreConfigurator = sslKeyStoreConfigurator;
        this.sslTrustStoreConfigurator = sslTrustStoreConfigurator;
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

    private SslConnectionFactory newSslConnectionFactory() {
        Ssl sslConfig = connectorConfig.ssl();

        SslContextFactory factory = new JDiscSslContextFactory();

        sslKeyStoreConfigurator.configure(new DefaultSslKeyStoreContext(factory));
        sslTrustStoreConfigurator.configure(new DefaultSslTrustStoreContext(factory));

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

        // NOTE: ^TLS_RSA_.*$ ciphers are disabled by default in Jetty 9.4.12+ (https://github.com/eclipse/jetty.project/issues/2807)
        // JDisc will allow these ciphers by default to support older clients (e.g. Java 8u60 and curl 7.29.0)
        String[] excludedCiphersWithoutTlsRsaExclusion = Arrays.stream(factory.getExcludeCipherSuites())
                .filter(cipher -> !cipher.equals("^TLS_RSA_.*$"))
                .toArray(String[]::new);
        factory.setExcludeCipherSuites(excludedCiphersWithoutTlsRsaExclusion);

        setStringArrayParameter(
                factory, sslConfig.excludeProtocol(), ExcludeProtocol::name, SslContextFactory::setExcludeProtocols);
        setStringArrayParameter(
                factory, sslConfig.includeProtocol(), IncludeProtocol::name, SslContextFactory::setIncludeProtocols);
        setStringArrayParameter(
                factory, sslConfig.excludeCipherSuite(), ExcludeCipherSuite::name, SslContextFactory::setExcludeCipherSuites);
        setStringArrayParameter(
                factory, sslConfig.includeCipherSuite(), IncludeCipherSuite::name, SslContextFactory::setIncludeCipherSuites);

        factory.setKeyManagerFactoryAlgorithm(sslConfig.sslKeyManagerFactoryAlgorithm());
        factory.setProtocol(sslConfig.protocol());
        return new SslConnectionFactory(factory, HttpVersion.HTTP_1_1.asString());
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
