// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.monitoring;

import com.google.inject.Inject;
import com.yahoo.cloud.config.ZookeeperServerConfig;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;
import com.yahoo.container.jdisc.config.HealthMonitorConfig;
import com.yahoo.docproc.jdisc.metric.NullMetric;
import com.yahoo.jdisc.Metric;
import com.yahoo.log.LogLevel;
import com.yahoo.statistics.Statistics;
import com.yahoo.statistics.Counter;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Statistics for server. The statistics framework takes care of logging.
 *
 * @author Harald Musum
 * @since 4.2
 */
public class Metrics extends TimerTask implements MetricUpdaterFactory {

    private static final java.util.logging.Logger log = java.util.logging.Logger.getLogger(Metrics.class.getName());
    private static final String METRIC_REQUESTS = getMetricName("requests");
    private static final String METRIC_FAILED_REQUESTS = getMetricName("failedRequests");
    private static final String METRIC_FREE_MEMORY = getMetricName("freeMemory");
    private static final String METRIC_LATENCY = getMetricName("latency");

    private final Counter requests;
    private final Counter failedRequests;
    private final Counter procTimeCounter;
    private final Metric metric;
    private final ZKMetricUpdater zkMetricUpdater;

    // TODO The map is the key for now
    private final Map<Map<String, String>, MetricUpdater> metricUpdaters = new ConcurrentHashMap<>();
    private final Timer timer = new Timer();

    @Inject
    public Metrics(Metric metric, Statistics statistics, HealthMonitorConfig healthMonitorConfig, ZookeeperServerConfig zkServerConfig) {
        this.metric = metric;
        requests = createCounter(METRIC_REQUESTS, statistics);
        failedRequests = createCounter(METRIC_FAILED_REQUESTS, statistics);
        procTimeCounter = createCounter("procTime", statistics);

        log.log(LogLevel.DEBUG, "Metric update interval is " + healthMonitorConfig.snapshot_interval() + " seconds");
        long intervalMs = (long) (healthMonitorConfig.snapshot_interval() * 1000);
        timer.scheduleAtFixedRate(this, 20000, intervalMs);
        zkMetricUpdater = new ZKMetricUpdater(zkServerConfig, 19500, intervalMs);
    }

    public static Metrics createTestMetrics() {
        NullMetric metric = new NullMetric();
        Statistics.NullImplementation statistics = new Statistics.NullImplementation();
        HealthMonitorConfig.Builder builder = new HealthMonitorConfig.Builder();
        builder.snapshot_interval(60.0);
        ZookeeperServerConfig.Builder zkBuilder = new ZookeeperServerConfig.Builder().myid(1);
        return new Metrics(metric, statistics, new HealthMonitorConfig(builder), new ZookeeperServerConfig(zkBuilder));
    }

    private Counter createCounter(String name, Statistics statistics) {
        return new Counter(name, statistics, false);
    }


    void incrementRequests(Metric.Context metricContext) {
        requests.increment(1);
        metric.add(METRIC_REQUESTS, 1, metricContext);
    }

    void incrementFailedRequests(Metric.Context metricContext) {
        failedRequests.increment(1);
        metric.add(METRIC_FAILED_REQUESTS, 1, metricContext);
    }

    void incrementProcTime(long increment, Metric.Context metricContext) {
        procTimeCounter.increment(increment);
        metric.set(METRIC_LATENCY, increment, metricContext);
    }

    public long getRequests() {
        return requests.get();
    }

    public Metric getMetric() {
        return metric;
    }

    public MetricUpdater removeMetricUpdater(Map<String, String> dimensions) {
        return metricUpdaters.remove(dimensions);
    }

    public static Map<String, String> createDimensions(ApplicationId applicationId) {
        final Map<String, String> properties = new LinkedHashMap<>();
        properties.put("tenantName", applicationId.tenant().value());
        properties.put("applicationName", applicationId.application().value());
        properties.put("applicationInstance", applicationId.instance().value());
        return properties;
    }

    public static Map<String, String> createDimensions(TenantName tenant) {
        final Map<String, String> properties = new LinkedHashMap<>();
        properties.put("tenantName", tenant.value());
        return properties;
    }

    public synchronized MetricUpdater getOrCreateMetricUpdater(Map<String, String> dimensions) {
        if (metricUpdaters.containsKey(dimensions)) {
            return metricUpdaters.get(dimensions);
        }
        MetricUpdater metricUpdater = new MetricUpdater(this, dimensions);
        metricUpdaters.put(dimensions, metricUpdater);
        return metricUpdater;
    }

    @Override
    public void run() {
        for (MetricUpdater metricUpdater : metricUpdaters.values()) {
            log.log(LogLevel.DEBUG, "Running metric updater for static values for " + metricUpdater.getDimensions());
            for (Map.Entry<String, Number> fixedMetric : metricUpdater.getStaticMetrics().entrySet()) {
                log.log(LogLevel.DEBUG, "Setting " + fixedMetric.getKey());
                metric.set(fixedMetric.getKey(), fixedMetric.getValue(), metricUpdater.getMetricContext());
            }
        }
        setRegularMetrics();
        zkMetricUpdater.getZKMetrics().forEach((attr, val) -> metric.set(attr, val, null));
        timer.purge();
    }

    private void setRegularMetrics() {
        metric.set(METRIC_FREE_MEMORY, Runtime.getRuntime().freeMemory(), null);
    }

    void increment(String metricName, Metric.Context context) {
        metric.add(metricName, 1, context);
    }

    void set(String metricName, Number value, Metric.Context context) {
        metric.set(metricName, value, context);
    }

    static String getMetricName(String name) {
        return "configserver." + name;
    }
}
