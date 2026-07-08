// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import ai.vespa.sampling.ProbabilisticSampleRate;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.http.ConnectorConfig;
import org.eclipse.jetty.io.ConnectionStatistics;
import org.eclipse.jetty.server.ConnectionFactory;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author bjorncs
 */
class JDiscServerConnector extends ServerConnector {

    // Keep a cache of metric contexts to avoid creating new contexts for each request. Metric context creation is expensive.
    private final Map<String, Metric.Context> requestMetricContexts;

    public static final String REQUEST_ATTRIBUTE = JDiscServerConnector.class.getName();
    private final Metric.Context connectorMetricCtx;
    private final ConnectionStatistics statistics;
    private final ConnectorConfig config;
    private final Metric metric;
    private final String connectorName;
    private final int listenPort;
    private final List<RequestContentLogging> requestContentLogging;

    record RequestContentLogging(String pathPrefix, ProbabilisticSampleRate samplingRate, long maxSize) {}

    JDiscServerConnector(ConnectorConfig config, Metric metric, Server server, ConnectionFactory... factories) {
        super(server, factories);
        this.config = config;
        this.metric = metric;
        this.connectorName = config.name();
        this.listenPort = config.listenPort();
        this.connectorMetricCtx = metric.createContext(createConnectorDimensions(listenPort, connectorName, 0));
        this.requestMetricContexts = createRequestMetricContexts(metric, listenPort);

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

        this.requestContentLogging = config.accessLog().content().stream()
                .map(e -> new RequestContentLogging(
                        e.pathPrefix(), ProbabilisticSampleRate.withSystemDefaults(e.sampleRate()), e.maxSize()))
                .toList();
    }

    List<RequestContentLogging> requestContentLogging() { return requestContentLogging; }

    public ConnectionStatistics getStatistics() {
        return statistics;
    }

    public Metric.Context getConnectorMetricContext() {
        return connectorMetricCtx;
    }

    Metric.Context createRequestMetricContext(Request request) {
        var method = request.getMethod();
        var requestMetricCtx = requestMetricContexts.get(method);
        if (requestMetricCtx != null) return requestMetricCtx;
        // Fallback if request metric context is not available
        return createRequestMetricContext(metric, listenPort, method);
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

    static Metric.Context createRequestMetricContext(Metric metric, int listenPort, String method) {
        Map<String, Object> dimensions = new HashMap<>(2);
        dimensions.put(MetricDefinitions.PORT_DIMENSION, listenPort);
        dimensions.put(MetricDefinitions.METHOD_DIMENSION, method);
        return metric.createContext(dimensions);
    }

    private static Map<String, Metric.Context> createRequestMetricContexts(Metric metric, int listenPort) {
        var requestMetricContexts = new HashMap<String, Metric.Context>();
        for (var method : RequestUtils.SUPPORTED_METHODS) {
            requestMetricContexts.put(method, createRequestMetricContext(metric, listenPort, method));
        }
        return requestMetricContexts;
    }
}
