// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.metrics.simple;

import com.yahoo.api.annotations.Beta;
import com.yahoo.metrics.simple.UntypedMetric.AssumedType;

/**
 * A counter metric. Create a counter by declaring it with
 * {@link MetricReceiver#declareCounter(String)} or
 * {@link MetricReceiver#declareCounter(String, Point)}.
 *
 * @author steinar
 */
@Beta
public class Counter {
    private final Point defaultPosition;
    private final String name;
    private final MetricReceiver metricReceiver;

    Counter(String name, Point defaultPosition, MetricReceiver receiver) {
        this.name = name;
        this.defaultPosition = defaultPosition;
        this.metricReceiver = receiver;
    }

    /**
     * Increase the dimension-less/zero-point value of this counter by 1.
     */
    public void add() {
        add(1L, defaultPosition);
    }

    /**
     * Add to the dimension-less/zero-point value of this counter.
     *
     * @param n the amount by which to increase this counter
     */
    public void add(long n) {
        add(n, defaultPosition);
    }

    /**
     * Increase this metric at the given point by 1.
     *
     * @param p the point in the metric space at which to increase this metric by 1
     */
    public void add(Point p) {
        add(1L, p);
    }

    /**
     * Add to this metric at the given point.
     *
     * @param n
     *            the amount by which to increase this counter
     * @param p
     *            the point in the metric space at which to add to the metric
     */
    public void add(long n, Point p) {
        metricReceiver.update(new Sample(new Measurement(n), new Identifier(name, p), AssumedType.COUNTER));
    }

    /**
     * Create a PointBuilder with default dimension values as given when this
     * counter was declared.
     *
     * @return a PointBuilder reflecting the default dimension values of this
     *         counter
     */
    public PointBuilder builder() {
        return new PointBuilder(defaultPosition);
    }
}
