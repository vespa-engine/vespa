// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.http.ConnectorConfig;
import com.yahoo.jdisc.http.ConnectorConfig.Ssl;
import com.yahoo.jdisc.http.ConnectorConfig.Ssl.PemKeyStore;
import com.yahoo.jdisc.http.SecretStore;
import com.yahoo.jdisc.http.ssl.ReaderForPath;
import com.yahoo.jdisc.http.ssl.SslKeyStore;
import com.yahoo.jdisc.http.ssl.SslKeyStoreFactory;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.server.ConnectionFactory;
import org.eclipse.jetty.server.ServerConnectionStatistics;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import javax.servlet.ServletRequest;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Field;
import java.net.Socket;
import java.net.SocketException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.google.common.io.Closeables.closeQuietly;
import static com.yahoo.jdisc.http.ConnectorConfig.Ssl.KeyStoreType.Enum.JKS;
import static com.yahoo.jdisc.http.ConnectorConfig.Ssl.KeyStoreType.Enum.PEM;
import static com.yahoo.jdisc.http.server.jetty.Exceptions.throwUnchecked;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 * @since 5.21.0
 */
public class ConnectorFactory {

    private final static Logger log = Logger.getLogger(ConnectorFactory.class.getName());
    private final ConnectorConfig connectorConfig;
    private final SslKeyStoreFactory sslKeyStoreFactory;
    private final SecretStore secretStore;

    @Inject
    public ConnectorFactory(ConnectorConfig connectorConfig, SslKeyStoreFactory sslKeyStoreFactory, SecretStore secretStore) {
        this.connectorConfig = connectorConfig;
        this.sslKeyStoreFactory = sslKeyStoreFactory;
        this.secretStore = secretStore;

        if (connectorConfig.ssl().enabled())
            validateSslConfig(connectorConfig);
    }

    // TODO: can be removed when we have dedicated SSL config in services.xml
    private static void validateSslConfig(ConnectorConfig config) {
        ConnectorConfig.Ssl ssl = config.ssl();

        if (ssl.keyStoreType() == JKS) {
            if (! ssl.pemKeyStore().keyPath().isEmpty()
                    || ! ssl.pemKeyStore().certificatePath().isEmpty())
                throw new IllegalArgumentException(
                        "Setting pemKeyStore attributes does not make sense when keyStoreType==JKS.");
        }
        if (ssl.keyStoreType() == PEM) {
            if (! ssl.keyStorePath().isEmpty())
                throw new IllegalArgumentException(
                        "Setting keyStorePath does not make sense when keyStoreType==PEM");
        }
    }

    public ConnectorConfig getConnectorConfig() {
        return connectorConfig;
    }

    public ServerConnector createConnector(final Metric metric, final Server server, final ServerSocketChannel ch, Map<Path, FileChannel> keyStoreChannels) {
        final ServerConnector connector;
        if (connectorConfig.ssl().enabled()) {
            connector = new JDiscServerConnector(connectorConfig, metric, server, ch,
                                                 newSslConnectionFactory(keyStoreChannels),
                                                 newHttpConnectionFactory());
        } else {
            connector = new JDiscServerConnector(connectorConfig, metric, server, ch,
                                                 newHttpConnectionFactory());
        }
        connector.setPort(connectorConfig.listenPort());
        connector.setName(connectorConfig.name());
        connector.setAcceptQueueSize(connectorConfig.acceptQueueSize());
        connector.setReuseAddress(connectorConfig.reuseAddress());
        connector.setSoLingerTime(connectorConfig.soLingerTime());
        connector.setIdleTimeout((long)(connectorConfig.idleTimeout() * 1000.0));
        connector.setStopTimeout((long)(connectorConfig.stopTimeout() * 1000.0));
        return connector;
    }

    private HttpConnectionFactory newHttpConnectionFactory() {
        final HttpConfiguration httpConfig = new HttpConfiguration();
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
    private SslConnectionFactory newSslConnectionFactory(Map<Path, FileChannel> keyStoreChannels) {
        Ssl sslConfig = connectorConfig.ssl();

        final SslContextFactory factory = new SslContextFactory();
        if (!sslConfig.excludeProtocol().isEmpty()) {
            final String[] prots = new String[sslConfig.excludeProtocol().size()];
            for (int i = 0; i < prots.length; i++) {
                prots[i] = sslConfig.excludeProtocol(i).name();
            }
            factory.setExcludeProtocols(prots);
        }
        if (!sslConfig.includeProtocol().isEmpty()) {
            final String[] prots = new String[sslConfig.includeProtocol().size()];
            for (int i = 0; i < prots.length; i++) {
                prots[i] = sslConfig.includeProtocol(i).name();
            }
            factory.setIncludeProtocols(prots);
        }
        if (!sslConfig.excludeCipherSuite().isEmpty()) {
            final String[] ciphs = new String[sslConfig.excludeCipherSuite().size()];
            for (int i = 0; i < ciphs.length; i++) {
                ciphs[i] = sslConfig.excludeCipherSuite(i).name();
            }
            factory.setExcludeCipherSuites(ciphs);

        }
        if (!sslConfig.includeCipherSuite().isEmpty()) {
            final String[] ciphs = new String[sslConfig.includeCipherSuite().size()];
            for (int i = 0; i < ciphs.length; i++) {
                ciphs[i] = sslConfig.includeCipherSuite(i).name();
            }
            factory.setIncludeCipherSuites(ciphs);

        }


        Optional<String> password = Optional.of(sslConfig.keyDbKey()).
                filter(key -> !key.isEmpty()).map(secretStore::getSecret);

        switch (sslConfig.keyStoreType()) {
            case PEM:
                factory.setKeyStore(getKeyStore(sslConfig.pemKeyStore(), keyStoreChannels));
                if (password.isPresent()) {
                    log.warning("Encrypted PEM key stores are not supported.");
                }
                break;
            case JKS:
                factory.setKeyStorePath(sslConfig.keyStorePath());
                factory.setKeyStoreType(sslConfig.keyStoreType().toString());
                factory.setKeyStorePassword(password.orElseThrow(passwordRequiredForJKSKeyStore("key")));
                break;
        }

        if (!sslConfig.trustStorePath().isEmpty()) {
            factory.setTrustStorePath(sslConfig.trustStorePath());
            factory.setTrustStoreType(sslConfig.trustStoreType().toString());
            factory.setTrustStorePassword(password.orElseThrow(passwordRequiredForJKSKeyStore("trust")));
        }

        factory.setKeyManagerFactoryAlgorithm(sslConfig.sslKeyManagerFactoryAlgorithm());
        factory.setProtocol(sslConfig.protocol());
        return new SslConnectionFactory(factory, HttpVersion.HTTP_1_1.asString());
    }

    @SuppressWarnings("ThrowableInstanceNeverThrown")
    private Supplier<RuntimeException> passwordRequiredForJKSKeyStore(String type) {
        return () -> new RuntimeException(String.format("Password is required for JKS %s store", type));
    }

    private KeyStore getKeyStore(PemKeyStore pemKeyStore, Map<Path, FileChannel> keyStoreChannels) {
        Preconditions.checkArgument(!pemKeyStore.certificatePath().isEmpty(), "Missing certificate path.");
        Preconditions.checkArgument(!pemKeyStore.keyPath().isEmpty(), "Missing key path.");

        class KeyStoreReaderForPath implements AutoCloseable {
            private final Optional<FileChannel> channel;
            public final ReaderForPath readerForPath;


            KeyStoreReaderForPath(String pathString) {
                Path path = Paths.get(pathString);
                channel = Optional.ofNullable(keyStoreChannels.get(path));
                readerForPath = new ReaderForPath(
                        channel.map(this::getReader).orElseGet(() -> getReader(path)),
                        path);
            }

            private Reader getReader(FileChannel channel) {
                try {
                    channel.position(0);
                    return Channels.newReader(channel, StandardCharsets.UTF_8.newDecoder(), -1);
                } catch (IOException e) {
                    throw throwUnchecked(e);
                }

            }

            private Reader getReader(Path path) {
                try {
                    return Files.newBufferedReader(path);
                } catch (IOException e) {
                    throw new RuntimeException("Failed opening " + path, e);
                }
            }

            @Override
            public void close()  {
                //channels are reused
                if (!channel.isPresent()) {
                    closeQuietly(readerForPath.reader);
                }
            }
        }

        try (KeyStoreReaderForPath certificateReader = new KeyStoreReaderForPath(pemKeyStore.certificatePath());
             KeyStoreReaderForPath keyReader = new KeyStoreReaderForPath(pemKeyStore.keyPath())) {
            SslKeyStore keyStore = sslKeyStoreFactory.createKeyStore(certificateReader.readerForPath,
                                                                     keyReader.readerForPath);
            return keyStore.loadJavaKeyStore();
        } catch (Exception e) {
            throw new RuntimeException("Failed setting up key store for " + pemKeyStore.keyPath() + ", " + pemKeyStore.certificatePath(), e);
        }
    }

    public static class JDiscServerConnector extends ServerConnector {
        public static final String REQUEST_ATTRIBUTE = JDiscServerConnector.class.getName();
        private final static Logger log = Logger.getLogger(JDiscServerConnector.class.getName());
        private final Metric.Context metricCtx;
        private final ServerConnectionStatistics statistics;
        private final boolean tcpKeepAlive;
        private final boolean tcpNoDelay;
        private final ServerSocketChannel channelOpenedByActivator;

        private JDiscServerConnector(
                final ConnectorConfig config,
                final Metric metric,
                final Server server,
                final ServerSocketChannel channelOpenedByActivator,
                final ConnectionFactory... factories) {
            super(server, factories);
            this.channelOpenedByActivator = channelOpenedByActivator;
            this.tcpKeepAlive = config.tcpKeepAliveEnabled();
            this.tcpNoDelay = config.tcpNoDelay();
            this.metricCtx = createMetricContext(config, metric);

            this.statistics = new ServerConnectionStatistics();
            addBean(statistics);
        }

        private Metric.Context createMetricContext(ConnectorConfig config, Metric metric) {
            Map<String, Object> props = new TreeMap<>();
            props.put(JettyHttpServer.Metrics.NAME_DIMENSION, config.name());
            props.put(JettyHttpServer.Metrics.PORT_DIMENSION, config.listenPort());
            return metric.createContext(props);
        }

        @Override
        protected void configure(final Socket socket) {
            super.configure(socket);
            try {
                socket.setKeepAlive(tcpKeepAlive);
                socket.setTcpNoDelay(tcpNoDelay);
            } catch (final SocketException ignored) {

            }
        }

        @Override
        public void open() throws IOException {
            if (channelOpenedByActivator == null) {
                log.log(Level.INFO, "No channel set by activator, opening channel ourselves.");
                try {
                    super.open();
                } catch (RuntimeException e) {
                    log.log(Level.SEVERE, "failed org.eclipse.jetty.server.Server open() with port "+getPort());
                    throw e;
                }
                return;
            }
            log.log(Level.INFO, "Using channel set by activator: " + channelOpenedByActivator);

            channelOpenedByActivator.socket().setReuseAddress(getReuseAddress());
            int localPort = channelOpenedByActivator.socket().getLocalPort();
            try {
                uglySetLocalPort(localPort);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                throw new RuntimeException("Could not set local port.", e);
            }
            if (localPort <= 0) {
                throw new IOException("Server channel not bound");
            }
            addBean(channelOpenedByActivator);
            channelOpenedByActivator.configureBlocking(true);
            addBean(channelOpenedByActivator);

            try {
                uglySetChannel(channelOpenedByActivator);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                throw new RuntimeException("Could not set server channel.", e);
            }
        }

        private void uglySetLocalPort(int localPort) throws NoSuchFieldException, IllegalAccessException {
            Field localPortField = ServerConnector.class.getDeclaredField("_localPort");
            localPortField.setAccessible(true);
            localPortField.set(this, localPort);
        }

        private void uglySetChannel(ServerSocketChannel channelOpenedByActivator) throws NoSuchFieldException, IllegalAccessException {
            Field acceptChannelField = ServerConnector.class.getDeclaredField("_acceptChannel");
            acceptChannelField.setAccessible(true);
            acceptChannelField.set(this, channelOpenedByActivator);
        }

        public ServerConnectionStatistics getStatistics() { return statistics; }

        public Metric.Context getMetricContext() { return metricCtx; }

        public static JDiscServerConnector fromRequest(ServletRequest request) {
            return (JDiscServerConnector)request.getAttribute(REQUEST_ATTRIBUTE);
        }
    }
}
