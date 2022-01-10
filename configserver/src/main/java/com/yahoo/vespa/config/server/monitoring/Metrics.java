// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.monitoring;

import com.yahoo.component.annotation.Inject;
import com.yahoo.cloud.config.ZookeeperServerConfig;
import com.yahoo.component.AbstractComponent;
import com.yahoo.concurrent.DaemonThreadFactory;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;
import com.yahoo.container.jdisc.config.HealthMonitorConfig;
import com.yahoo.docproc.jdisc.metric.NullMetric;
import com.yahoo.jdisc.Metric;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Metrics for config server.
 *
 * @author Harald Musum
 */
public class Metrics extends AbstractComponent implements MetricUpdaterFactory, Runnable {

    private static final java.util.logging.Logger log = java.util.logging.Logger.getLogger(Metrics.class.getName());
    private static final String METRIC_REQUESTS = getMetricName("requests");
    private static final String METRIC_FAILED_REQUESTS = getMetricName("failedRequests");
    private static final String METRIC_FREE_MEMORY = getMetricName("freeMemory");
    private static final String METRIC_LATENCY = getMetricName("latency");

    private final Metric metric;
    private final Optional<ZKMetricUpdater> zkMetricUpdater;

    // TODO The map is the key for now
    private final Map<Map<String, String>, MetricUpdater> metricUpdaters = new ConcurrentHashMap<>();
    private final Optional<ScheduledExecutorService> executorService;

    @Inject
    public Metrics(Metric metric, HealthMonitorConfig healthMonitorConfig, ZookeeperServerConfig zkServerConfig) {
        this(metric, healthMonitorConfig, zkServerConfig, true);
    }

    private Metrics(Metric metric, HealthMonitorConfig healthMonitorConfig,
                    ZookeeperServerConfig zkServerConfig, boolean createZkMetricUpdater) {
        this.metric = metric;

        if (createZkMetricUpdater) {
            log.log(Level.FINE, () -> "Metric update interval is " + healthMonitorConfig.snapshot_interval() + " seconds");
            long intervalMs = (long) (healthMonitorConfig.snapshot_interval() * 1000);
            executorService = Optional.of(new ScheduledThreadPoolExecutor(1, new DaemonThreadFactory("configserver-metrics")));
            executorService.get().scheduleAtFixedRate(this, 20000, intervalMs, TimeUnit.MILLISECONDS);
            zkMetricUpdater = Optional.of(new ZKMetricUpdater(zkServerConfig, 19500, intervalMs));
        } else {
            executorService = Optional.empty();
            zkMetricUpdater = Optional.empty();
        }
    }

    public static Metrics createTestMetrics() {
        NullMetric metric = new NullMetric();
        HealthMonitorConfig.Builder builder = new HealthMonitorConfig.Builder();
        builder.snapshot_interval(60.0);
        ZookeeperServerConfig.Builder zkBuilder = new ZookeeperServerConfig.Builder().myid(1);
        return new Metrics(metric, new HealthMonitorConfig(builder), new ZookeeperServerConfig(zkBuilder), false);
    }

    void incrementRequests(Metric.Context metricContext) {
        metric.add(METRIC_REQUESTS, 1, metricContext);
    }

    void incrementFailedRequests(Metric.Context metricContext) {
        metric.add(METRIC_FAILED_REQUESTS, 1, metricContext);
    }

    void incrementProcTime(long increment, Metric.Context metricContext) {
        metric.set(METRIC_LATENCY, increment, metricContext);
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
            log.log(Level.FINE, () -> "Running metric updater for static values for " + metricUpdater.getDimensions());
            for (Map.Entry<String, Number> fixedMetric : metricUpdater.getStaticMetrics().entrySet()) {
                log.log(Level.FINE, () -> "Setting " + fixedMetric.getKey());
                metric.set(fixedMetric.getKey(), fixedMetric.getValue(), metricUpdater.getMetricContext());
            }
        }
        setRegularMetrics();
        zkMetricUpdater.ifPresent(updater -> updater.getZKMetrics().forEach((attr, val) -> metric.set(attr, val, null)));
    }

    public void deconstruct() {
        executorService.ifPresent(ExecutorService::shutdown);
        zkMetricUpdater.ifPresent(ZKMetricUpdater::shutdown);
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
