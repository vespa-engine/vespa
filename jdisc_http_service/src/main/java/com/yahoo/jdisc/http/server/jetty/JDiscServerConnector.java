// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.http.ConnectorConfig;
import org.eclipse.jetty.server.ConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnectionStatistics;
import org.eclipse.jetty.server.ServerConnector;

import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.Socket;
import java.net.SocketException;
import java.nio.channels.ServerSocketChannel;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author bjorncs
 */
class JDiscServerConnector extends ServerConnector {
    public static final String REQUEST_ATTRIBUTE = JDiscServerConnector.class.getName();
    private final static Logger log = Logger.getLogger(JDiscServerConnector.class.getName());
    private final Metric.Context metricCtx;
    private final ServerConnectionStatistics statistics;
    private final boolean tcpKeepAlive;
    private final boolean tcpNoDelay;
    private final ServerSocketChannel channelOpenedByActivator;
    private final Metric metric;
    private final String connectorName;
    private final int listenPort;

    JDiscServerConnector(ConnectorConfig config, Metric metric, Server server,
                         ServerSocketChannel channelOpenedByActivator, ConnectionFactory... factories) {
        super(server, factories);
        this.channelOpenedByActivator = channelOpenedByActivator;
        this.tcpKeepAlive = config.tcpKeepAliveEnabled();
        this.tcpNoDelay = config.tcpNoDelay();
        this.metricCtx = createMetricContext(config, metric);
        this.metric = metric;
        this.connectorName = config.name();
        this.listenPort = config.listenPort();

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
        } catch (SocketException ignored) {
        }
    }

    @Override
    public void open() throws IOException {
        if (channelOpenedByActivator == null) {
            log.log(Level.INFO, "No channel set by activator, opening channel ourselves.");
            try {
                super.open();
            } catch (RuntimeException e) {
                log.log(Level.SEVERE, "failed org.eclipse.jetty.server.Server open() with port " + getPort());
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

    private void uglySetChannel(ServerSocketChannel channelOpenedByActivator) throws NoSuchFieldException,
            IllegalAccessException {
        Field acceptChannelField = ServerConnector.class.getDeclaredField("_acceptChannel");
        acceptChannelField.setAccessible(true);
        acceptChannelField.set(this, channelOpenedByActivator);
    }

    public ServerConnectionStatistics getStatistics() {
        return statistics;
    }

    public Metric.Context getConnectorMetricContext() {
        return metricCtx;
    }

    public Metric.Context getRequestMetricContext(HttpServletRequest request) {
        Map<String, Object> props = new TreeMap<>();
        props.put(JettyHttpServer.Metrics.NAME_DIMENSION, connectorName);
        props.put(JettyHttpServer.Metrics.PORT_DIMENSION, listenPort);
        props.put(JettyHttpServer.Metrics.METHOD_DIMENSION, request.getMethod());
        return metric.createContext(props);
    }

    public static JDiscServerConnector fromRequest(ServletRequest request) {
        return (JDiscServerConnector) request.getAttribute(REQUEST_ATTRIBUTE);
    }

}
