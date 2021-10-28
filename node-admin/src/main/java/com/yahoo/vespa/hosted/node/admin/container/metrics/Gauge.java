// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.container.metrics;

/**
 * @author freva
 */
public class Gauge implements MetricValue {
    private final Object lock = new Object();

    private double value;

    public void sample(double x) {
        synchronized (lock) {
            this.value = x;
        }
    }

    @Override
    public Number getValue() {
        synchronized (lock) {
            return value;
        }
    }
}
