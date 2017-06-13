// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.metrics;

import java.util.concurrent.atomic.AtomicLong;

/**
 * @author thomasg
 */
public class CountMetric extends NumberMetric<AtomicLong> {
    public CountMetric(String name, MetricSet owner) {
        super(name, new AtomicLong(0), owner);
    }

    public void inc(long increment) {
        get().addAndGet(increment);
    }

}
