// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.metrics;

/**
 * @author Simon Thoresen Hult
 */
class SummedLongValueMetric extends ValueMetric<Long> {

    public SummedLongValueMetric(String name, String tags, String description, MetricSet owner) {
        super(name, tags, description, owner);
    }

    public SummedLongValueMetric(SummedLongValueMetric other, CopyType copyType, MetricSet owner) {
        super(other, copyType, owner);
    }
}

