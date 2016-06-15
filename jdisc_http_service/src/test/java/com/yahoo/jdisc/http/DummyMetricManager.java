// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http;

import com.google.inject.AbstractModule;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.application.MetricConsumer;

import java.util.HashMap;
import java.util.Map;

/**
 * @author <a href="mailto:ssameer@yahoo-inc.com">ssameer</a>
 *         Date: 2/15/13
 *         Time: 11:49 AM
 */
public class DummyMetricManager extends AbstractModule implements MetricConsumer {

    private final Map<String, Integer> metrics = new HashMap<>();
    private Map<String, ?> lastContextDimensions;

    @Override
    protected void configure() {
        bind(MetricConsumer.class).toInstance(this);
    }

    @Override
    public void add(String key, Number val, Metric.Context ctx) {
        synchronized (metrics) {
            metrics.put(key, get(key) + val.intValue());
        }
    }

    @Override
    public void set(String key, Number val, Metric.Context ctx) {
        synchronized (metrics) {
            metrics.put(key, val.intValue());
        }
    }

    @Override
    public Metric.Context createContext(Map<String, ?> dimensions) {
        lastContextDimensions = dimensions;
        return new Metric.Context() { };
    }

    public Map<String, ?> getLastContextDimensions() {
        return lastContextDimensions;
    }

    public int get(String key) {
        Integer val;
        synchronized (metrics) {
            val = metrics.get(key);
        }
        return val != null ? val : 0;
    }

}
