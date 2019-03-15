// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.fastsearch.test;

import com.yahoo.jdisc.Metric;

import java.util.Map;

public class MockMetric implements Metric {
    @Override
    public void set(String key, Number val, Context ctx) {
    }

    @Override
    public void add(String key, Number val, Context ctx) {
    }

    @Override
    public Context createContext(Map<String, ?> properties) {
        return null;
    }
}
