// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.metrics;

import com.yahoo.messagebus.metrics.MetricSet;

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
