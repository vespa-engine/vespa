package com.yahoo.vespa.hosted.dockerapi.metrics;

import com.google.inject.Inject;
import com.yahoo.metrics.simple.MetricReceiver;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author valerijf
 */
public class MetricReceiverWrapper {
    private final Map<String, MetricValue> metrics = new HashMap<>();
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

    public GaugeWrapper declageGauge(String name) {
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
