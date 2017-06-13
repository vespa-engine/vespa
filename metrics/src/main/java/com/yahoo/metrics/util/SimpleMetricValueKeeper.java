// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.metrics.util;

import java.util.Collection;
import com.yahoo.metrics.JoinBehavior;

/**
 * Simple implementation of the metric value keeper for use with snapshots. Only keeps
 * one instance of data, does not need to worry about threads, and should have a small
 * memory footprint.
 */
public class SimpleMetricValueKeeper<Value> implements MetricValueKeeper<Value> {
    private Value value;

    public SimpleMetricValueKeeper() {
    }

    public void add(Value v) {
        throw new UnsupportedOperationException("Not supported. This value keeper is not intended to be used for live metrics.");
    }

    public void set(Value v) {
        this.value = v;
    }

    public Value get(JoinBehavior joinBehavior) {
        return value;
    }

    public void reset() {
        value = null;
    }

    public Collection<Value> getDirectoryView() {
        return null;
    }

}
