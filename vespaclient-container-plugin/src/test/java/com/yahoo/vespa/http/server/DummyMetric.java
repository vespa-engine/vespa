// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.server;

import com.yahoo.jdisc.Metric;

import java.util.Map;

/**
 * @author Einar M R Rosenvinge
 * @since 5.1.20
 */
class DummyMetric implements Metric {

    @Override
    public void set(String key, Number val, Context ctx) {
    }

    @Override
    public void add(String key, Number val, Context ctx) {
    }

    @Override
    public Context createContext(Map<String, ?> properties) {
        return DummyContext.INSTANCE;
    }

    private static class DummyContext implements Context {
        private static final DummyContext INSTANCE = new DummyContext();
    }

}
