// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.metrics.jmx.core;

/**
 * <p>This interface represents a single metric. Implementations need to ensure that instances within a snapshot of
 * com.yahoo.jdisc.metrics.jmx.ConsumerContextMetric are in a consistent state</p>
 *
 * @author <a href="mailto:alain@yahoo-inc.com">Alain Wan Buen Cheong</a>
 */
public interface MetricUnit {

    /**
     * Returns a Number representation of the stored value(s)
     *
     * @return The stored value, or a single representation of the stored values
     */
    public Number getValue();

    /**
     * Adds the input value
     */
    public void addValue(Number value);

    /**
     * Copies data from the input {@link MetricUnit} and merges with the
     * current {@link MetricUnit}
     */
    public void addMetric(MetricUnit metricUnit);

    /**
     * Is this {@link MetricUnit} persistent
     */
    public boolean isPersistent();
}
