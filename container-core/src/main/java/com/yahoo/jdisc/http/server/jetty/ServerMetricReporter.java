// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.yahoo.concurrent.DaemonThreadFactory;
import com.yahoo.jdisc.Metric;
import org.eclipse.jetty.io.ConnectionStatistics;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandlerContainer;
import org.eclipse.jetty.server.handler.StatisticsHandler;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Reports server/connector specific metrics for Jdisc and Jetty
 *
 * @author bjorncs
 */
class ServerMetricReporter {

    private final ScheduledExecutorService executor =
            Executors.newScheduledThreadPool(1, new DaemonThreadFactory("jdisc-jetty-metric-reporter-"));
    private final Metric metric;
    private final Server jetty;

    ServerMetricReporter(Metric metric, Server jetty) {
        this.metric = metric;
        this.jetty = jetty;
    }

    void start() {
        executor.scheduleAtFixedRate(new ReporterTask(), 0, 2, TimeUnit.SECONDS);
    }

    void shutdown() {
        try {
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private class ReporterTask implements Runnable {

        private final Instant timeStarted = Instant.now();

        @Override
        public void run() {
            var collector = ResponseMetricAggregator.getBean(jetty);
            if (collector != null) setServerMetrics(collector);

            // reset statisticsHandler to preserve earlier behavior
            StatisticsHandler statisticsHandler = ((AbstractHandlerContainer) jetty.getHandler())
                    .getChildHandlerByClass(StatisticsHandler.class);
            if (statisticsHandler != null) {
                statisticsHandler.statsReset();
            }

            for (Connector connector : jetty.getConnectors()) {
                setConnectorMetrics((JDiscServerConnector)connector);
            }

            setJettyThreadpoolMetrics();
        }

        private void setServerMetrics(ResponseMetricAggregator statisticsCollector) {
            long timeSinceStarted = System.currentTimeMillis() - timeStarted.toEpochMilli();
            metric.set(MetricDefinitions.STARTED_MILLIS, timeSinceStarted, null);

            addResponseMetrics(statisticsCollector);
        }

        private void addResponseMetrics(ResponseMetricAggregator statisticsCollector) {
            statisticsCollector.reportSnapshot(metric);
        }

        private void setJettyThreadpoolMetrics() {
            QueuedThreadPool threadpool = (QueuedThreadPool) jetty.getThreadPool();
            metric.set(MetricDefinitions.JETTY_THREADPOOL_MAX_THREADS, threadpool.getMaxThreads(), null);
            metric.set(MetricDefinitions.JETTY_THREADPOOL_MIN_THREADS, threadpool.getMinThreads(), null);
            metric.set(MetricDefinitions.JETTY_THREADPOOL_RESERVED_THREADS, threadpool.getReservedThreads(), null);
            metric.set(MetricDefinitions.JETTY_THREADPOOL_BUSY_THREADS, threadpool.getBusyThreads(), null);
            metric.set(MetricDefinitions.JETTY_THREADPOOL_IDLE_THREADS, threadpool.getIdleThreads(), null);
            metric.set(MetricDefinitions.JETTY_THREADPOOL_TOTAL_THREADS, threadpool.getThreads(), null);
            metric.set(MetricDefinitions.JETTY_THREADPOOL_QUEUE_SIZE, threadpool.getQueueSize(), null);
        }

        private void setConnectorMetrics(JDiscServerConnector connector) {
            ConnectionStatistics statistics = connector.getStatistics();
            metric.set(MetricDefinitions.NUM_CONNECTIONS, statistics.getConnectionsTotal(), connector.getConnectorMetricContext());
            metric.set(MetricDefinitions.NUM_OPEN_CONNECTIONS, statistics.getConnections(), connector.getConnectorMetricContext());
            metric.set(MetricDefinitions.NUM_CONNECTIONS_OPEN_MAX, statistics.getConnectionsMax(), connector.getConnectorMetricContext());
            metric.set(MetricDefinitions.CONNECTION_DURATION_MAX, statistics.getConnectionDurationMax(), connector.getConnectorMetricContext());
            metric.set(MetricDefinitions.CONNECTION_DURATION_MEAN, statistics.getConnectionDurationMean(), connector.getConnectorMetricContext());
            metric.set(MetricDefinitions.CONNECTION_DURATION_STD_DEV, statistics.getConnectionDurationStdDev(), connector.getConnectorMetricContext());
        }

    }
}
