// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.yahoo.component.annotation.Inject;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.http.ConnectorConfig;
import com.yahoo.jdisc.http.SslProvider;
import com.yahoo.jdisc.http.ssl.impl.DefaultConnectorSsl;
import com.yahoo.security.tls.MixedMode;
import com.yahoo.security.tls.TransportSecurityUtils;
import org.eclipse.jetty.alpn.server.ALPNServerConnectionFactory;
import org.eclipse.jetty.http.HttpCompliance;
import org.eclipse.jetty.http.UriCompliance;
import org.eclipse.jetty.http2.server.AbstractHTTP2ServerConnectionFactory;
import org.eclipse.jetty.http2.server.HTTP2CServerConnectionFactory;
import org.eclipse.jetty.http2.server.HTTP2ServerConnectionFactory;
import org.eclipse.jetty.server.ConnectionFactory;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.DetectorConnectionFactory;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.ProxyConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.util.HostPort;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static com.yahoo.security.tls.MixedMode.DISABLED;
import static com.yahoo.security.tls.MixedMode.PLAINTEXT_CLIENT_MIXED_SERVER;
import static com.yahoo.security.tls.MixedMode.TLS_CLIENT_MIXED_SERVER;

/**
 * @author Einar M R Rosenvinge
 * @author bjorncs
 */
public class ConnectorFactory {

    private static final Logger log = Logger.getLogger(ConnectorFactory.class.getName());

    private final ConnectorConfig connectorConfig;
    private final SslProvider sslProvider;

    @Inject
    public ConnectorFactory(ConnectorConfig connectorConfig,
                            SslProvider sslProvider) {
        runtimeConnectorConfigValidation(connectorConfig);
        this.connectorConfig = connectorConfig;
        this.sslProvider = sslProvider;
    }

    // Perform extra connector config validation that can only be performed at runtime,
    // e.g. due to TLS configuration through environment variables.
    private static void runtimeConnectorConfigValidation(ConnectorConfig config) {
        validateProxyProtocolConfiguration(config);
    }

    private static void validateProxyProtocolConfiguration(ConnectorConfig config) {
        ConnectorConfig.ProxyProtocol proxyProtocolConfig = config.proxyProtocol();
        if (proxyProtocolConfig.enabled()) {
            boolean tlsMixedModeEnabled = TransportSecurityUtils.getInsecureMixedMode() != DISABLED;
            if (!isSslEffectivelyEnabled(config) || tlsMixedModeEnabled) {
                throw new IllegalArgumentException("Proxy protocol can only be enabled if connector is effectively HTTPS only");
            }
        }
    }

    public ConnectorConfig getConnectorConfig() {
        return connectorConfig;
    }

    public ServerConnector createConnector(final Metric metric, final Server server, JettyConnectionLogger connectionLogger,
                                           ConnectionMetricAggregator connectionMetricAggregator) {
        return new JDiscServerConnector(
                connectorConfig, metric, server, connectionLogger, connectionMetricAggregator,
                createConnectionFactories(metric).toArray(ConnectionFactory[]::new));
    }

    private List<ConnectionFactory> createConnectionFactories(Metric metric) {
        boolean vespaTlsEnabled = TransportSecurityUtils.isTransportSecurityEnabled() && connectorConfig.implicitTlsEnabled();
        MixedMode tlsMixedMode = TransportSecurityUtils.getInsecureMixedMode();
        if (connectorConfig.ssl().enabled() || (vespaTlsEnabled && tlsMixedMode == DISABLED)) {
            return connectionFactoriesForHttps(metric);
        } else if (vespaTlsEnabled) {
            if (tlsMixedMode != TLS_CLIENT_MIXED_SERVER && tlsMixedMode != PLAINTEXT_CLIENT_MIXED_SERVER) {
                throw new IllegalArgumentException("Unknown mixed mode " + tlsMixedMode);
            }
            return connectionFactoriesForTlsMixedMode(metric);
        } else {
            return connectorConfig.http2Enabled()
                    ? List.of(newHttp1ConnectionFactory(), newHttp2ClearTextConnectionFactory())
                    : List.of(newHttp1ConnectionFactory());
        }
    }

    private List<ConnectionFactory> connectionFactoriesForHttps(Metric metric) {
        List<ConnectionFactory> factories = new ArrayList<>();
        ConnectorConfig.ProxyProtocol proxyProtocolConfig = connectorConfig.proxyProtocol();
        HttpConnectionFactory http1Factory = newHttp1ConnectionFactory();
        ALPNServerConnectionFactory alpnFactory;
        SslConnectionFactory sslFactory;
        if (connectorConfig.http2Enabled()) {
            alpnFactory = newAlpnConnectionFactory();
            sslFactory = newSslConnectionFactory(metric, alpnFactory);
        } else {
            alpnFactory = null;
            sslFactory = newSslConnectionFactory(metric, http1Factory);
        }
        if (proxyProtocolConfig.enabled()) {
            factories.add(newProxyProtocolConnectionFactory(sslFactory, proxyProtocolConfig.mixedMode()));
        }
        factories.add(sslFactory);
        if (connectorConfig.http2Enabled()) factories.add(alpnFactory);
        factories.add(http1Factory);
        if (connectorConfig.http2Enabled()) factories.add(newHttp2ConnectionFactory());
        return List.copyOf(factories);
    }

    private List<ConnectionFactory> connectionFactoriesForTlsMixedMode(Metric metric) {
        log.warning(String.format("TLS mixed mode enabled for port %d - HTTP/2 and proxy-protocol are not supported",
                connectorConfig.listenPort()));
        HttpConnectionFactory httpFactory = newHttp1ConnectionFactory();
        SslConnectionFactory sslFactory = newSslConnectionFactory(metric, httpFactory);
        DetectorConnectionFactory detectorFactory = newDetectorConnectionFactory(sslFactory);
        return List.of(detectorFactory, httpFactory, sslFactory);
    }

    private HttpConfiguration newHttpConfiguration() {
        HttpConfiguration httpConfig = new HttpConfiguration();
        httpConfig.setSendDateHeader(true);
        httpConfig.setSendServerVersion(false);
        httpConfig.setSendXPoweredBy(false);
        httpConfig.setHeaderCacheSize(connectorConfig.headerCacheSize());
        httpConfig.setOutputBufferSize(connectorConfig.outputBufferSize());
        httpConfig.setRequestHeaderSize(connectorConfig.requestHeaderSize());
        httpConfig.setResponseHeaderSize(connectorConfig.responseHeaderSize());

        // Disable use of ByteBuffer.allocateDirect()
        httpConfig.setUseInputDirectByteBuffers(false);
        httpConfig.setUseOutputDirectByteBuffers(false);

        httpConfig.setHttpCompliance(HttpCompliance.RFC7230);
        // TODO Vespa 9 Use default URI compliance (LEGACY == old Jetty 9.4 compliance)
        httpConfig.setUriCompliance(UriCompliance.LEGACY);
        if (isSslEffectivelyEnabled(connectorConfig)) {
            // Explicitly disable SNI checking as Jetty's SNI checking trust manager is not part of our SSLContext trust manager chain
            httpConfig.addCustomizer(new SecureRequestCustomizer(false, false, -1, false));
        }
        String serverNameFallback = connectorConfig.serverName().fallback();
        if (!serverNameFallback.isBlank()) httpConfig.setServerAuthority(new HostPort(serverNameFallback));
        return httpConfig;
    }

    private HttpConnectionFactory newHttp1ConnectionFactory() {
        return new HttpConnectionFactory(newHttpConfiguration());
    }

    private HTTP2ServerConnectionFactory newHttp2ConnectionFactory() {
        HTTP2ServerConnectionFactory factory = new HTTP2ServerConnectionFactory(newHttpConfiguration());
        setHttp2Config(factory);
        return factory;
    }

    private HTTP2CServerConnectionFactory newHttp2ClearTextConnectionFactory() {
        HTTP2CServerConnectionFactory factory = new HTTP2CServerConnectionFactory(newHttpConfiguration());
        setHttp2Config(factory);
        return factory;
    }

    private void setHttp2Config(AbstractHTTP2ServerConnectionFactory factory) {
        factory.setStreamIdleTimeout(toMillis(connectorConfig.http2().streamIdleTimeout()));
        factory.setMaxConcurrentStreams(connectorConfig.http2().maxConcurrentStreams());
        factory.setInitialSessionRecvWindow(1 << 24);
        factory.setInitialStreamRecvWindow(1 << 20);
    }

    private SslConnectionFactory newSslConnectionFactory(Metric metric, ConnectionFactory wrappedFactory) {
        var fac = new SslConnectionFactory(createSslContextFactory(), wrappedFactory.getProtocol());
        fac.setDirectBuffersForDecryption(false);
        fac.setDirectBuffersForEncryption(false);
        fac.addBean(new SslHandshakeFailedListener(metric, connectorConfig.name(), connectorConfig.listenPort()));
        return fac;
    }

    private SslContextFactory.Server createSslContextFactory() {
        DefaultConnectorSsl ssl = new DefaultConnectorSsl();
        sslProvider.configureSsl(ssl, connectorConfig.name(), connectorConfig.listenPort());
        return ssl.createSslContextFactory();
    }

    private ALPNServerConnectionFactory newAlpnConnectionFactory() {
        ALPNServerConnectionFactory factory = new ALPNServerConnectionFactory("h2", "http/1.1");
        factory.setDefaultProtocol("http/1.1");
        return factory;
    }

    private DetectorConnectionFactory newDetectorConnectionFactory(ConnectionFactory.Detecting... alternatives) {
        // Note: Detector connection factory with single alternative will fallback to next protocol in connection factory list
        return new DetectorConnectionFactory(alternatives);
    }

    private ProxyConnectionFactory newProxyProtocolConnectionFactory(ConnectionFactory wrapped, boolean mixedMode) {
        return mixedMode
                ? new ProxyConnectionFactory(wrapped.getProtocol())
                : new MandatoryProxyConnectionFactory(wrapped.getProtocol());
    }

    private static boolean isSslEffectivelyEnabled(ConnectorConfig config) {
        return config.ssl().enabled()
                || (config.implicitTlsEnabled() && TransportSecurityUtils.isTransportSecurityEnabled());
    }

    private static long toMillis(double seconds) { return (long)(seconds * 1000); }

    /**
     * A {@link ProxyConnectionFactory} which disables the default behaviour of upgrading to
     * next protocol when proxy protocol is not detected.
     */
    private static class MandatoryProxyConnectionFactory extends ProxyConnectionFactory {
        MandatoryProxyConnectionFactory(String next) { super(next); }
        @Override protected String findNextProtocol(Connector __) { return null; }
    }


}
