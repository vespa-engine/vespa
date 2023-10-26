// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.monitoring;

import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.config.server.ServerCache;
import com.yahoo.vespa.config.server.RequestHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.yahoo.vespa.config.server.monitoring.Metrics.getMetricName;
/**
 * @author hmusum
 */
// TODO javadoc, thread non-safeness maybe
public class MetricUpdater {
    private static final String METRIC_UNKNOWN_HOSTS = getMetricName("unknownHostRequests");
    private static final String METRIC_SESSION_CHANGE_ERRORS = getMetricName("sessionChangeErrors");
    private static final String METRIC_NEW_SESSIONS = getMetricName("newSessions");
    private static final String METRIC_PREPARED_SESSIONS = getMetricName("preparedSessions");
    private static final String METRIC_ACTIVATED_SESSIONS = getMetricName("activeSessions");
    private static final String METRIC_DEACTIVATED_SESSIONS = getMetricName("inactiveSessions");
    private static final String METRIC_ADDED_SESSIONS = getMetricName("addedSessions");
    private static final String METRIC_REMOVED_SESSIONS = getMetricName("removedSessions");
    private static final String METRIC_ZK_CONNECTION_LOST = getMetricName("zkConnectionLost");
    private static final String METRIC_ZK_RECONNECTED = getMetricName("zkReconnected");
    private static final String METRIC_ZK_CONNECTED = getMetricName("zkConnected");
    private static final String METRIC_ZK_SUSPENDED = getMetricName("zkSuspended");
    private static final String METRIC_TENANTS = getMetricName("tenants");
    private static final String METRIC_HOSTS = getMetricName("hosts");
    private static final String METRIC_APPLICATIONS = getMetricName("applications");
    private static final String METRIC_CACHE_CONFIG_ELEMENTS = getMetricName("cacheConfigElems");
    private static final String METRIC_CACHE_CONFIG_CHECKSUMS = getMetricName("cacheChecksumElems");
    private static final String METRIC_DELAYED_RESPONSES = getMetricName("delayedResponses");
    private static final String METRIC_RPCSERVER_WORK_QUEUE_SIZE = getMetricName("rpcServerWorkQueueSize");


    private final Metrics metrics;
    private final Map<String, String> dimensions;
    private final Metric.Context metricContext;
    private final Map<String, Number> staticMetrics = new ConcurrentHashMap<>();

    public MetricUpdater(Metrics metrics, Map<String, String> dimensions) {
        this.metrics = metrics;
        this.dimensions = dimensions;
        metricContext = createContext(metrics, dimensions);
    }

    public void incrementRequests() {
        metrics.incrementRequests(metricContext);
    }

    public void incrementFailedRequests() {
        metrics.incrementFailedRequests(metricContext);
    }

    public void incrementProcTime(long increment) {
        metrics.incrementProcTime(increment, metricContext);
    }

    /**
     * Sets the count for number of config elements in the {@link ServerCache}
     *
     * @param elems number of elements
     */
    public void setCacheConfigElems(long elems) {
        staticMetrics.put(METRIC_CACHE_CONFIG_ELEMENTS, elems);
    }

    /**
     * Sets the count for number of checksum elements in the {@link ServerCache}
     *
     * @param elems number of elements
     */
    public void setCacheChecksumElems(long elems) {
        staticMetrics.put(METRIC_CACHE_CONFIG_CHECKSUMS, elems);
    }

    /**
     * Sets the number of outstanding responses (unchanged config in long poll)
     *
     * @param elems number of elements
     */
    public void setDelayedResponses(long elems) {
        staticMetrics.put(METRIC_DELAYED_RESPONSES, elems);
    }

    private void setStaticMetric(String name, int size) {
        staticMetrics.put(name, size);
    }

    /**
     * Increment the number of requests where we were unable to map host to a {@link RequestHandler}.
     */
    public void incUnknownHostRequests() {
        metrics.increment(METRIC_UNKNOWN_HOSTS, metricContext);
    }

    private Metric.Context createContext(Metrics metrics, Map<String, String> dimensions) {
        if (metrics == null) return null;

        return metrics.getMetric().createContext(dimensions);
    }

    public Map<String, Number> getStaticMetrics() {
        return staticMetrics;
    }

    public Metric.Context getMetricContext() {
        return metricContext;
    }

    public Map<String, String> getDimensions() {
        return dimensions;
    }

    /**
     * Increment the number of errors from changed sessions.
     */
    public void incSessionChangeErrors() {
        metrics.increment(METRIC_SESSION_CHANGE_ERRORS, metricContext);
    }

    /**
     * Set the number of new sessions.
     */
    public void setNewSessions(int numNew) {
        setStaticMetric(METRIC_NEW_SESSIONS, numNew);
    }

    /**
     * Set the number of prepared sessions.
     */
    public void setPreparedSessions(int numPrepared) {
        setStaticMetric(METRIC_PREPARED_SESSIONS, numPrepared);
    }

    /**
     * Set the number of activated sessions.
     */
    public void setActivatedSessions(int numActivated) {
        setStaticMetric(METRIC_ACTIVATED_SESSIONS, numActivated);
    }

    /**
     * Set the number of deactivated sessions.
     */
    public void setDeactivatedSessions(int numDeactivated) {
        setStaticMetric(METRIC_DEACTIVATED_SESSIONS, numDeactivated);
    }

    /**
     * Increment the number of removed sessions.
     */
    public void incRemovedSessions() {
        metrics.increment(METRIC_REMOVED_SESSIONS, metricContext);
    }

    /**
     * Increment the number of added sessions.
     */
    public void incAddedSessions() {
        metrics.increment(METRIC_ADDED_SESSIONS, metricContext);
    }

    public static MetricUpdater createTestUpdater() {
        return new MetricUpdater(Metrics.createTestMetrics(), null);
    }

    /**
     * Increment the number of ZK connection losses.
     */
    public void incZKConnectionLost() {
        metrics.increment(METRIC_ZK_CONNECTION_LOST, metricContext);
    }

    /**
     * Increment the number of ZK connection establishments.
     */
    public void incZKConnected() {
        metrics.increment(METRIC_ZK_CONNECTED, metricContext);
    }

    /**
     * Increment the number of ZK connection suspended.
     */
    public void incZKSuspended() {
        metrics.increment(METRIC_ZK_SUSPENDED, metricContext);
    }

    /**
     * Increment the number of ZK reconnections.
     */
    public void incZKReconnected() {
        metrics.increment(METRIC_ZK_RECONNECTED, metricContext);
    }

    /**
     * Set the number of tenants.
     */
    public void setTenants(int numTenants) {
        setStaticMetric(METRIC_TENANTS, numTenants);
    }

    /**
     * Set the number of hosts.
     */
    public void setHosts(int numHosts) {
        setStaticMetric(METRIC_HOSTS, numHosts);
    }

    /**
     * Set the number of applications.
     */
    public void setApplications(int numApplications) {
        setStaticMetric(METRIC_APPLICATIONS, numApplications);
    }

    public void setRpcServerQueueSize(int numQueued) {
        metrics.set(METRIC_RPCSERVER_WORK_QUEUE_SIZE, numQueued, metricContext);
    }
}
