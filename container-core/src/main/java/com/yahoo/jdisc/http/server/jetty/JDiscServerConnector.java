// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.http.ConnectorConfig;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.io.ConnectionStatistics;
import org.eclipse.jetty.server.ConnectionFactory;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;

import java.util.HashMap;
import java.util.Map;

/**
 * @author bjorncs
 */
class JDiscServerConnector extends ServerConnector {

    record MetricContextKey(String method, String protocol) {}

    // Keep a cache of metric contexts to avoid creating new contexts for each request. Metric context creation is expensive.
    private final Map<MetricContextKey, Metric.Context> requestMetricContexts;

    public static final String REQUEST_ATTRIBUTE = JDiscServerConnector.class.getName();
    private final Metric.Context connectorMetricCtx;
    private final ConnectionStatistics statistics;
    private final ConnectorConfig config;
    private final Metric metric;
    private final String connectorName;
    private final int listenPort;

    JDiscServerConnector(ConnectorConfig config, Metric metric, Server server, ConnectionFactory... factories) {
        super(server, factories);
        this.config = config;
        this.metric = metric;
        this.connectorName = config.name();
        this.listenPort = config.listenPort();
        this.connectorMetricCtx = metric.createContext(createConnectorDimensions(listenPort, connectorName, 0));
        this.requestMetricContexts = createRequestMetricContexts(metric, listenPort, connectorName);

        this.statistics = new ConnectionStatistics();
        setAcceptedTcpNoDelay(config.tcpNoDelay());
        addBean(statistics);
        ConnectorConfig.Throttling throttlingConfig = config.throttling();
        if (throttlingConfig.enabled()) {
            new ConnectionThrottler(this, throttlingConfig).registerWithConnector();
        }
        setPort(config.listenPort());
        setName(config.name());
        setAcceptQueueSize(config.acceptQueueSize());
        setReuseAddress(config.reuseAddress());
        long idleTimeout = (long)(config.idleTimeout() * 1000);
        setIdleTimeout(idleTimeout);
        long shutdownIdleTimeout = (long) (config.shutdownIdleTimeout() * 1000);
        // Ensure shutdown idle timeout is less than idle timeout and stop timeout
        setShutdownIdleTimeout(Math.min(shutdownIdleTimeout, Math.min(idleTimeout, server.getStopTimeout())));
    }

    public ConnectionStatistics getStatistics() {
        return statistics;
    }

    public Metric.Context getConnectorMetricContext() {
        return connectorMetricCtx;
    }

    Metric.Context createRequestMetricContext(Request request) {
        var method = request.getMethod();
        var protocol = request.getConnectionMetaData().getProtocol();
        var requestMetricCtx = requestMetricContexts.get(new MetricContextKey(method, protocol));
        if (requestMetricCtx != null) return requestMetricCtx;
        // Fallback if request metric context is not available
        return createRequestMetricContext(metric, listenPort, connectorName, method, protocol);
    }

    ConnectorConfig connectorConfig() {
        return config;
    }

    int listenPort() {
        return listenPort;
    }

    private static Map<String, Object> createConnectorDimensions(int listenPort, String connectorName, int reservedSize) {
        Map<String, Object> props = new HashMap<>(reservedSize + 2);
        props.put(MetricDefinitions.NAME_DIMENSION, connectorName);
        props.put(MetricDefinitions.PORT_DIMENSION, listenPort);
        return props;
    }

    static Metric.Context createRequestMetricContext(
            Metric metric, int listenPort, String connectorName, String method, String protocol) {
        var dimensions = createConnectorDimensions(listenPort, connectorName, 2);
        dimensions.put(MetricDefinitions.METHOD_DIMENSION, method);
        dimensions.put(MetricDefinitions.PROTOCOL_DIMENSION, protocol);
        return metric.createContext(dimensions);
    }

    private static Map<MetricContextKey, Metric.Context> createRequestMetricContexts(
            Metric metric, int listenPort, String connectorName) {
        var requestMetricContexts = new HashMap<MetricContextKey, Metric.Context>();
        for (var method : RequestUtils.SUPPORTED_METHODS) {
            for (var protocol : HttpVersion.values()) {
                requestMetricContexts.put(
                        new MetricContextKey(method, protocol.asString()),
                        createRequestMetricContext(metric, listenPort, connectorName, method, protocol.asString()));
            }
        }
        return requestMetricContexts;
    }
}
