// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.metrics;

import com.yahoo.messagebus.ErrorCode;

import java.util.HashMap;
import java.util.Map;

/**
 * @author thomasg
 */
public class RouteMetricSet extends MetricSet {
    public MetricSet allErrors = new MetricSet("errors");
    public MetricSet failures = new MetricSet("failures");
    public AverageMetric latency = new AverageMetric("latency", this);

    private Map<Integer, CountMetric> errorMap = new HashMap<Integer, CountMetric>();

    RouteMetricSet(String route) {
        super(route);
        setXmlTagName("messages");
        addMetric(allErrors);
        addMetric(failures);
    }

    public void addError(com.yahoo.messagebus.Error e) {
        CountMetric metric = errorMap.get(e.getCode());
        if (metric == null) {
            metric = new CountMetric(ErrorCode.getName(e.getCode()), allErrors);
            metric.setXmlTagName("error");
            errorMap.put(e.getCode(), metric);
        }
        metric.inc(1);
    }

    public void addFailure(com.yahoo.messagebus.Error e) {
        CountMetric metric = errorMap.get(e.getCode());
        if (metric == null) {
            metric = new CountMetric(ErrorCode.getName(e.getCode()), failures);
            metric.setXmlTagName("failure");
            errorMap.put(e.getCode(), metric);
        }
        metric.inc(1);
    }
}
