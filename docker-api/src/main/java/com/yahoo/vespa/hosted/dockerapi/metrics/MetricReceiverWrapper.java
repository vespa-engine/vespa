// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.dockerapi.metrics;

import com.google.inject.Inject;
import com.yahoo.metrics.simple.MetricReceiver;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Export metrics to both /state/v1/metrics and makes them available programatically.
 *
 * @author valerijf
 */
public class MetricReceiverWrapper {
    private final Map<String, MetricValue> metrics = new ConcurrentHashMap<>();
    private final MetricReceiver metricReceiver;

    @Inject
    public MetricReceiverWrapper(MetricReceiver metricReceiver) {
        this.metricReceiver = metricReceiver;
    }

    public CounterWrapper declareCounter(String name) {
        CounterWrapper counter = new CounterWrapper(metricReceiver.declareCounter(name));
        metrics.put(name, counter);
        return counter;
    }

    public GaugeWrapper declareGauge(String name) {
        GaugeWrapper gauge = new GaugeWrapper(metricReceiver.declareGauge(name));
        metrics.put(name, gauge);
        return gauge;
    }

    public Set<String> getMetricNames() {
        return new HashSet<>(metrics.keySet());
    }

    public MetricValue getMetricByName(String name) {
        return metrics.get(name);
    }
}
