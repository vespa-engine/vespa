// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.http.ConnectorConfig;
import org.eclipse.jetty.io.ConnectionStatistics;
import org.eclipse.jetty.server.ConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;

import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import java.net.Socket;
import java.net.SocketException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author bjorncs
 */
class JDiscServerConnector extends ServerConnector {

    public static final String REQUEST_ATTRIBUTE = JDiscServerConnector.class.getName();
    private final Metric.Context metricCtx;
    private final Map<RequestDimensions, Metric.Context> requestMetricContextCache = new ConcurrentHashMap<>();
    private final ConnectionStatistics statistics;
    private final ConnectorConfig config;
    private final boolean tcpKeepAlive;
    private final boolean tcpNoDelay;
    private final Metric metric;
    private final String connectorName;
    private final int listenPort;

    JDiscServerConnector(ConnectorConfig config, Metric metric, Server server, ConnectionFactory... factories) {
        super(server, factories);
        this.config = config;
        this.tcpKeepAlive = config.tcpKeepAliveEnabled();
        this.tcpNoDelay = config.tcpNoDelay();
        this.metric = metric;
        this.connectorName = config.name();
        this.listenPort = config.listenPort();
        this.metricCtx = metric.createContext(createConnectorDimensions(listenPort, connectorName));

        this.statistics = new ConnectionStatistics();
        addBean(statistics);
        ConnectorConfig.Throttling throttlingConfig = config.throttling();
        if (throttlingConfig.enabled()) {
            new ConnectionThrottler(this, throttlingConfig).registerWithConnector();
        }
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

    public ConnectionStatistics getStatistics() {
        return statistics;
    }

    public Metric.Context getConnectorMetricContext() {
        return metricCtx;
    }

    public Metric.Context getRequestMetricContext(HttpServletRequest request) {
        String method = request.getMethod();
        String scheme = request.getScheme();
        boolean clientAuthenticated = request.getAttribute(com.yahoo.jdisc.http.servlet.ServletRequest.SERVLET_REQUEST_X509CERT) != null;
        var requestDimensions = new RequestDimensions(method, scheme, clientAuthenticated);
        return requestMetricContextCache.computeIfAbsent(requestDimensions, ignored -> {
            Map<String, Object> dimensions = createConnectorDimensions(listenPort, connectorName);
            dimensions.put(MetricDefinitions.METHOD_DIMENSION, method);
            dimensions.put(MetricDefinitions.SCHEME_DIMENSION, scheme);
            dimensions.put(MetricDefinitions.CLIENT_AUTHENTICATED_DIMENSION, Boolean.toString(clientAuthenticated));
            return metric.createContext(dimensions);
        });
    }

    public static JDiscServerConnector fromRequest(ServletRequest request) {
        return (JDiscServerConnector) request.getAttribute(REQUEST_ATTRIBUTE);
    }

    ConnectorConfig connectorConfig() {
        return config;
    }

    int listenPort() {
        return listenPort;
    }

    private static Map<String, Object> createConnectorDimensions(int listenPort, String connectorName) {
        Map<String, Object> props = new HashMap<>();
        props.put(MetricDefinitions.NAME_DIMENSION, connectorName);
        props.put(MetricDefinitions.PORT_DIMENSION, listenPort);
        return props;
    }

    private static class RequestDimensions {
        final String method;
        final String scheme;
        final boolean clientAuthenticated;

        RequestDimensions(String method, String scheme, boolean clientAuthenticated) {
            this.method = method;
            this.scheme = scheme;
            this.clientAuthenticated = clientAuthenticated;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            RequestDimensions that = (RequestDimensions) o;
            return clientAuthenticated == that.clientAuthenticated &&
                    Objects.equals(method, that.method) &&
                    Objects.equals(scheme, that.scheme);
        }

        @Override
        public int hashCode() {
            return Objects.hash(method, scheme, clientAuthenticated);
        }
    }

}
