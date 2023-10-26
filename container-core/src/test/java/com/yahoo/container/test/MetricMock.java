// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.test;

import com.yahoo.jdisc.Metric;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple mock for use whne testing metrics.
 *
 * @author bjorncs
 */
public class MetricMock implements Metric {
    public static class SimpleMetricContext implements Context {
        public final Map<String, String> dimensions;

        @SuppressWarnings("unchecked")
        SimpleMetricContext(Map<String, ?> dimensions) {
            this.dimensions = (Map<String, String>) dimensions;
        }
    }

    public static class Invocation {
        public final Number val;
        public final Context ctx;
        public Invocation(Number val, Context ctx) {
            this.val = val;
            this.ctx = ctx;
        }
    }

    private final Map<String, Invocation> addInvocations = new ConcurrentHashMap<>();

    public Map<String, Invocation> innvocations() {
        return addInvocations;
    }

    @Override
    public void add(String key, Number val, Context ctx) {
        addInvocations.put(key, new Invocation(val, ctx));
    }

    @Override
    public void set(String key, Number val, Context ctx) { addInvocations.put(key, new Invocation(val, ctx)); }

    @Override
    public Context createContext(Map<String, ?> properties) {
        return new SimpleMetricContext(properties);
    }
}
