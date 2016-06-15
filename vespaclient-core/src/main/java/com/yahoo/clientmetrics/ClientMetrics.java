// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.clientmetrics;

import com.yahoo.messagebus.Reply;
import com.yahoo.metrics.*;
import com.yahoo.text.XMLWriter;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * @author thomasg
 */
public class ClientMetrics {

    MetricSet topSet;
    SumMetric sum;
    List<String> routes = new ArrayList<String>();

    public ClientMetrics() {
        topSet = new SimpleMetricSet("routes", "", "", null);
        sum = new SumMetric("total", "", "Messages sent to all routes", topSet);
    }

    public MetricSet getMetricSet() {
        return topSet;
    }

    public void addRouteMetricSet(RouteMetricSet metric) {
        topSet.registerMetric(metric);
        sum.addMetricToSum(metric);
        routes.add(metric.getRoute());
    }
}
