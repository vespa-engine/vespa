package com.yahoo.vespa.hosted.dockerapi.metrics;


import com.yahoo.metrics.simple.Gauge;

public class GaugeWrapper implements MetricValue {
    private final Gauge gauge;
    private double value;

    GaugeWrapper(Gauge gauge) {
        this.gauge = gauge;
    }

    public void sample(double x) {
        gauge.sample(x);
        this.value = x;
    }

    public Number getValue() {
        return value;
    }
}
