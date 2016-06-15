// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.metrics;

import com.yahoo.concurrent.CopyOnWriteHashMap;
import com.yahoo.messagebus.Reply;
import com.yahoo.messagebus.routing.Route;

/**
 * @author thomasg
 */
public class MessageBusMetricSet extends MetricSet {
    public MetricSet protocols = new MetricSet("protocols");

    private final CopyOnWriteHashMap<String, RouteMetricSet> routeMetrics = new CopyOnWriteHashMap<String, RouteMetricSet>();

    public MessageBusMetricSet() {
        super("messagebus");
        addMetric(protocols);
    }

    public RouteMetricSet getRouteMetrics(Route r) {
        String route = r.toString();
        RouteMetricSet metric = routeMetrics.get(route);
        if (metric == null) {
            synchronized (routeMetrics) {
                metric = routeMetrics.get(route);
                if (metric == null) {
                    metric = new RouteMetricSet(route);
                    addMetric(metric);
                    routeMetrics.put(route, metric);
                }
            }
        }

        return metric;
    }

    public void updateMetrics(Reply reply, Route r) {

    }

}
