package com.yahoo.vespa.hosted.dockerapi;

import com.yahoo.metrics.simple.Gauge;
import com.yahoo.metrics.simple.MetricReceiver;

/**
 * @author valerijf
 */
public class DockerMetrics {
    private static final String METRIC_NAME_NUMBER_RUNNING_CONTAINERS = "running_containers";

    private static MetricReceiver metricReceiver;
    private static Gauge runningContainersMetric;

    public static void init(MetricReceiver receiver) {
        metricReceiver = receiver;
        runningContainersMetric = receiver.declareGauge(METRIC_NAME_NUMBER_RUNNING_CONTAINERS);
    }

    public static void updateNumberRunningContainers(int numberRunningContainers) {
        runningContainersMetric.sample(numberRunningContainers);
    }
}
