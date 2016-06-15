// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.metrics.jmx.core;

/**
 * <p>This class represents a counter value</p>
 *
 * @author <a href="mailto:alain@yahoo-inc.com">Alain Wan Buen Cheong</a>
 */
public final class CounterMetricUnit implements MetricUnit {

    private volatile long value = 0;

    public CounterMetricUnit() {}

    @Override
    public final Number getValue() {
        return value;
    }

    @Override
    public void addValue(Number value) {
        this.value += value.longValue();
    }

    @Override
    public void addMetric(MetricUnit metricUnit) {
        if (! (metricUnit instanceof CounterMetricUnit)) {
            throw new IllegalArgumentException(metricUnit.getClass().getName());
        }
        this.value += ((CounterMetricUnit)metricUnit).value;
    }

    @Override
    public boolean isPersistent() {
        return true;
    }
}
