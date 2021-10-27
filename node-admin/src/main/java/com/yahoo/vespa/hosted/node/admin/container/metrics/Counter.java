// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.container.metrics;

/**
 * @author freva
 */
public class Counter implements MetricValue {
    private final Object lock = new Object();

    private long value = 0;

    public void increment() {
        add(1L);
    }

    public void add(long n) {
        synchronized (lock) {
            value += n;
        }
    }

    @Override
    public Number getValue() {
        synchronized (lock) {
            return value;
        }
    }
}
