// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.service.model;

import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.Timer;

import java.util.function.Consumer;

public class ServiceMonitorMetrics {
    public static String SERVICE_MODEL_METRIC_PREFIX = "serviceModel.";

    private final Metric metric;
    private final Timer timer;

    public ServiceMonitorMetrics(Metric metric, Timer timer) {
        this.metric = metric;
        this.timer = timer;
    }

    public LatencyMeasurement startServiceModelSnapshotLatencyMeasurement() {
        Consumer<Double> atCompletion = elapsedSeconds ->
                setValue(metricKey("snapshot.latency"), elapsedSeconds);
        return new LatencyMeasurement(timer, atCompletion).start();
    }

    private static String metricKey(String suffix) {
        return SERVICE_MODEL_METRIC_PREFIX + suffix;
    }

    private void setValue(String key, Number number) {
        setValue(key, number, null);
    }

    private void setValue(String key, Number number, Metric.Context context) {
        metric.set(key, number, context);
    }
}
