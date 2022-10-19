// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.metrics.simple;

import com.yahoo.api.annotations.Beta;
import com.yahoo.metrics.simple.UntypedMetric.AssumedType;

/**
 * A gauge metric, i.e. a bucket of arbitrary sample values. Create a gauge
 * metric by declaring it with {@link MetricReceiver#declareGauge(String)} or
 * {@link MetricReceiver#declareGauge(String, Point)}.
 *
 * @author steinar
 */
@Beta
public class Gauge {

    private final Point defaultPosition;
    private final String name;
    private final MetricReceiver receiver;

    Gauge(String name, Point defaultPosition, MetricReceiver receiver) {
        this.name = name;
        this.defaultPosition = defaultPosition;
        this.receiver = receiver;
    }

    /**
     * Record a sample with default or no position.
     *
     * @param x
     *            sample value
     */
    public void sample(double x) {
        sample(x, defaultPosition);
    }

    /**
     * Record a sample at the given position.
     *
     * @param x
     *            sample value
     * @param p
     *            position/dimension values for the sample
     */
    public void sample(double x, Point p) {
        receiver.update(new Sample(new Measurement(x), new Identifier(name, p), AssumedType.GAUGE));
    }

    /**
     * Create a PointBuilder with the default dimension values reflecting those
     * given when this gauge was declared.
     *
     * @return a builder initialized with defaults from this metric instance
     */
    public PointBuilder builder() {
        return new PointBuilder(defaultPosition);
    }
}
