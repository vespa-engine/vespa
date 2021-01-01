// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.apputil.communication.http;

import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.clustercontroller.utils.util.MetricReporter;

import java.util.logging.Logger;

public class JDiscMetricWrapper implements MetricReporter {

    private final Object lock = new Object();
    private Metric m;

    private static class ContextWrapper implements MetricReporter.Context {
        Metric.Context wrappedContext;

        public ContextWrapper(Metric.Context wrapped) {
            this.wrappedContext = wrapped;
        }
    }

    public JDiscMetricWrapper(Metric m) {
        this.m = m;
    }

    public void updateMetricImplementation(Metric m) {
        synchronized (lock) {
            this.m = m;
        }
    }

    public void set(String s, Number number, MetricReporter.Context context) {
        synchronized (lock) {
            ContextWrapper cw = (ContextWrapper) context;
            m.set(s, number, cw == null ? null : cw.wrappedContext);
        }
    }

    public void add(String s, Number number, MetricReporter.Context context) {
        synchronized (lock) {
            ContextWrapper cw = (ContextWrapper) context;
            m.add(s, number, cw == null ? null : cw.wrappedContext);
        }
    }

    public MetricReporter.Context createContext(java.util.Map<java.lang.String,?> stringMap) {
        synchronized (lock) {
            return new ContextWrapper(m.createContext(stringMap));
        }
    }

}
