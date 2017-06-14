// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.metrics;

import java.io.Writer;

/**
 * @author thomasg
 */
public class ValueMetric<V extends Number> extends NumberMetric<V> {

    public ValueMetric(String name, V v, MetricSet owner) {
        super(name, v, owner);
    }
}
