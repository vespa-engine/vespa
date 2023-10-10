// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.clientmetrics;

import java.util.HashMap;
import java.util.Map;

/**
 * @author thomasg
 */
public class ClientMetrics {

    Map<String, RouteMetricSet> routes = new HashMap<>();

    public ClientMetrics() {

    }

    public void addRouteMetricSet(RouteMetricSet metric) {
        routes.put(metric.getRoute(), metric);
    }
}
