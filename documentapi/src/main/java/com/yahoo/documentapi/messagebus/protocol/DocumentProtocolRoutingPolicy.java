// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol;

import com.yahoo.messagebus.metrics.MetricSet;
import com.yahoo.messagebus.routing.RoutingPolicy;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * @author thomasg
 */
public interface DocumentProtocolRoutingPolicy extends RoutingPolicy {
    public MetricSet getMetrics();
}
