// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.metrics;

/**
 * A final metric set.
 */
public final class SimpleMetricSet extends MetricSet {

    public SimpleMetricSet(String name, String tags, String description) {
        this(name, tags, description, null);
    }

    public SimpleMetricSet(String name, String tags, String description, MetricSet owner) {
        super(name, tags, description, owner);
    }

    public SimpleMetricSet(SimpleMetricSet other, Metric.CopyType copyType, MetricSet owner, boolean includeUnused) {
        super(other, copyType, owner, includeUnused);
    }

    @Override
    public Metric clone(Metric.CopyType type, MetricSet owner, boolean includeUnused)
        { return new SimpleMetricSet(this, type, owner, includeUnused); }

}
