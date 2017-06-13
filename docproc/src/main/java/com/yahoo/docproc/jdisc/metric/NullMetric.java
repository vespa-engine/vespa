// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.docproc.jdisc.metric;

import com.yahoo.jdisc.Metric;

import java.util.Map;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 */
public class NullMetric implements Metric {
    @Override
    public void set(String key, Number val, Context ctx) {
    }

    @Override
    public void add(String key, Number val, Context ctx) {
    }

    @Override
    public Context createContext(Map<String, ?> properties) {
        return NullContext.INSTANCE;
    }

    private static class NullContext implements Context {
        private static final NullContext INSTANCE = new NullContext();
    }
}
