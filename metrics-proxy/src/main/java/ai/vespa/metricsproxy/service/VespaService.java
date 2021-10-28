// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.service;

import ai.vespa.metricsproxy.metric.HealthMetric;
import ai.vespa.metricsproxy.metric.Metric;
import ai.vespa.metricsproxy.metric.Metrics;
import ai.vespa.metricsproxy.metric.model.DimensionId;
import ai.vespa.metricsproxy.metric.model.ServiceId;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;


/**
 * Represents a Vespa service
 *
 * @author jobergum
 */
public class VespaService implements Comparable<VespaService> {

    private static final Map<DimensionId, String> EMPTY_DIMENSIONS = Collections.emptyMap();
    private static final String DEFAULT_MONITORING_PREFIX = "vespa";
    public static final String SEPARATOR = ".";

    private final String instanceName;
    private final String configId;
    private final String serviceName;
    private final ServiceId serviceId;
    private final Map<DimensionId, String> dimensions;

    private volatile int pid = -1;
    private volatile String state = "UNKNOWN";
    private volatile boolean isAlive;

    // Used to keep the last polled system metrics for service
    private final AtomicReference<Metrics> systemMetrics = new AtomicReference<>();

    private final int statePort;

    private final RemoteHealthMetricFetcher remoteHealthMetricFetcher;
    private final RemoteMetricsFetcher remoteMetricsFetcher;


    // Used to keep track of log level when health or metrics requests fail
    private final AtomicInteger metricsFetchCount = new AtomicInteger(0);
    private final AtomicInteger healthFetchCount = new AtomicInteger(0);


    public static VespaService create(String name, String id, int statePort) {
        return create(name,id, statePort, DEFAULT_MONITORING_PREFIX, EMPTY_DIMENSIONS);
    }

    public static VespaService create(String name, String id, int statePort, String monitoringName, Map<DimensionId, String> dimensions) {
        String serviceName = name.replaceAll("\\d*$", "");
        return new VespaService(serviceName, name, id, statePort, monitoringName, dimensions);
    }

    VespaService(String serviceName, String configId) {
        this(serviceName, serviceName, configId);
    }

    VespaService(String serviceName, String instanceName, String configId) {
        this(serviceName, instanceName, configId, -1, DEFAULT_MONITORING_PREFIX, EMPTY_DIMENSIONS);
    }

    private VespaService(String serviceName, String instanceName, String configId,
                         int statePort, String monitoringPrefix,
                         Map<DimensionId, String> dimensions) {
        this.serviceName = serviceName;
        this.instanceName = instanceName;
        serviceId = ServiceId.toServiceId(monitoringPrefix + SEPARATOR + serviceName);
        this.configId = configId;
        this.statePort = statePort;
        this.dimensions = dimensions;
        this.systemMetrics.set(new Metrics());
        this.isAlive = false;
        this.remoteMetricsFetcher = (this.statePort> 0) ? new RemoteMetricsFetcher(this, this.statePort) : new DummyMetricsFetcher(this);
        this.remoteHealthMetricFetcher = (this.statePort > 0) ? new RemoteHealthMetricFetcher(this, this.statePort) : new DummyHealthMetricFetcher(this);
    }

    /**
     * The name used for this service in the monitoring system:
     * monitoring-system-name.serviceName
     */
    public ServiceId getMonitoringName() {
        return serviceId;
    }

    @Override
    public int compareTo(VespaService other) {
        return this.getInstanceName().compareTo(other.getInstanceName());
    }

    /**
     * Get the service name/type. E.g 'searchnode', but not 'searchnode2'
     *
     * @return the service name
     */
    public String getServiceName() {
        return this.serviceName;
    }

    /**
     * Get the instance name. E.g searchnode2
     *
     * @return the instance service name
     */
    public String getInstanceName() {
        return this.instanceName;
    }

    public Map<DimensionId, String> getDimensions() {
        return dimensions;
    }

    /**
     * @return The health of this service
     */
    public HealthMetric getHealth() {
        HealthMetric healthMetric = remoteHealthMetricFetcher.getHealth(healthFetchCount.get());
        healthFetchCount.getAndIncrement();
        return healthMetric;
    }

    /**
     * Gets the system metrics for this service
     *
     * @return System metrics
     */
    public Metrics getSystemMetrics() {
        return systemMetrics.get();
    }

    /**
     * Get the Metrics registered for this service. Metrics are fetched over HTTP
     * if a metric http port has been defined, otherwise from log file
     */
    public void consumeMetrics(MetricsParser.Consumer consumer) {
        remoteMetricsFetcher.getMetrics(consumer, metricsFetchCount.get());
        metricsFetchCount.getAndIncrement();
    }

    private static class CollectMetrics implements MetricsParser.Consumer {
        private final Metrics metrics = new Metrics();
        @Override
        public void consume(Metric metric) {
            metrics.add(metric);
        }
    }
    public final Metrics getMetrics() {
        CollectMetrics collector = new CollectMetrics();
        consumeMetrics(collector);
        return collector.metrics;
    }

    /**
     * Gets the config id of this service
     *
     * @return the config id
     */
    public String getConfigId() {
        return configId;
    }

    /**
     * The current pid of this service
     *
     * @return The pid
     */
    public int getPid() {
        return this.pid;
    }

    /**
     * update the pid of this service
     *
     * @param pid The pid that this service runs as
     */
    public void setPid(int pid) {
        this.pid = pid;
    }

    /**
     * Get the string representation of the state of this service
     *
     * @return string representing the state of this service - obtained from config-sentinel
     */
    public String getState() {
        return state;
    }

    /**
     * Update the state of this service
     *
     * @param state the new state
     */
    public void setState(String state) {
        this.state = state;
    }

    /**
     * Check if this pid/service is running
     *
     * @return true if the service is alive (e.g the pid is running)
     */
    public boolean isAlive() {
        return (isAlive && (pid >= 0));
    }

    @Override
    public String toString() {
        return instanceName + ":" + pid + ":" + state + ":" + configId;
    }

    public void setAlive(boolean alive) {
        this.isAlive = alive;
    }

    public synchronized void setSystemMetrics(Metrics metrics) {
        systemMetrics.set(metrics);
    }

}
