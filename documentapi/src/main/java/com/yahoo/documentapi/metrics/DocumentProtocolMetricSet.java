// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.metrics;

import com.yahoo.messagebus.metrics.MetricSet;

import java.util.ArrayList;
import java.util.List;

/**
 * @author thomasg
 */
public class DocumentProtocolMetricSet extends MetricSet {
    public MetricSet routingPolicyMetrics = new MetricSet("routingpolicies");

    public DocumentProtocolMetricSet() {
        super("document");
        addMetric(routingPolicyMetrics);
    }

}
