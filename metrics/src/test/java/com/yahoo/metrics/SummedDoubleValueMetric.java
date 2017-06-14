// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.metrics;

/**
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen Hult</a>
 */
class SummedDoubleValueMetric extends ValueMetric<Double> {

    public SummedDoubleValueMetric(String name, String tags, String description, MetricSet owner) {
        super(name, tags, description, owner);
    }

    public SummedDoubleValueMetric(SummedDoubleValueMetric other, CopyType copyType, MetricSet owner) {
        super(other, copyType, owner);
    }
}
