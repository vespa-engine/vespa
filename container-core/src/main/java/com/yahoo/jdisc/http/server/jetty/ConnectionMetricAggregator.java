// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.http.ServerConfig;
import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.server.HttpChannel;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.util.component.AbstractLifeCycle;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicLong;

import static com.yahoo.jdisc.http.server.jetty.RequestUtils.getConnector;
import static com.yahoo.jdisc.http.server.jetty.RequestUtils.isHttpServerConnection;

/**
 * @author bjorncs
 */
class ConnectionMetricAggregator extends AbstractLifeCycle implements Connection.Listener, HttpChannel.Listener{

    private final SimpleConcurrentIdentityHashMap<Connection, ConnectionMetrics> connectionsMetrics = new SimpleConcurrentIdentityHashMap<>();

    private final Metric metricAggregator;
    private final Collection<String> monitoringHandlerPaths;

    ConnectionMetricAggregator(ServerConfig serverConfig, Metric metricAggregator) {
        this.monitoringHandlerPaths = serverConfig.metric().monitoringHandlerPaths();
        this.metricAggregator = metricAggregator;
    }

    @Override public void onOpened(Connection connection) {}

    @Override
    public void onClosed(Connection connection) {
        if (isHttpServerConnection(connection)) {
            connectionsMetrics.remove(connection).ifPresent(metrics ->
                    metricAggregator.set(MetricDefinitions.REQUESTS_PER_CONNECTION, metrics.requests.get(), metrics.metricContext));
        }
    }

    @Override
    public void onRequestBegin(Request request) {
        if (monitoringHandlerPaths.stream()
                .anyMatch(pathPrefix -> request.getRequestURI().startsWith(pathPrefix))){
            return;
        }
        Connection connection = request.getHttpChannel().getConnection();
        if (isHttpServerConnection(connection)) {
            ConnectionMetrics metrics = this.connectionsMetrics.computeIfAbsent(
                    connection,
                    () -> new ConnectionMetrics(getConnector(request).getConnectorMetricContext()));
            metrics.requests.incrementAndGet();
        }
    }

    private static class ConnectionMetrics {
        final AtomicLong requests = new AtomicLong();
        final Metric.Context metricContext;

        ConnectionMetrics(Metric.Context metricContext) {
            this.metricContext = metricContext;
        }
    }
}
