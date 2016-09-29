package com.yahoo.vespa.hosted.dockerapi.metrics;


import com.yahoo.metrics.simple.Gauge;

/**
 * Forwards sample to {@link com.yahoo.metrics.simple.Gauge} to be displayed in /state/v1/metrics,
 * while also saving the value so it can be accessed programatically later.
 *
 * @author valerijf
 */
public class GaugeWrapper implements MetricValue {
    private final Gauge gauge;
    private double value;

    GaugeWrapper(Gauge gauge) {
        this.gauge = gauge;
    }

    public void sample(double x) {
        synchronized (gauge) {
            gauge.sample(x);
            this.value = x;
        }
    }

    @Override
    public Number getValue() {
        return value;
    }
}
