// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.server;

import com.yahoo.jdisc.Metric;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author ollivir
 */
public final class CollectingMetric implements Metric {
    private final Context DUMMY_CONTEXT = new Context() {};
    private final Map<String, AtomicLong> values = new ConcurrentHashMap<>();

    public CollectingMetric() {}

    @Override
    public void set(String key, Number val, Context ctx) {
        values.computeIfAbsent(key, ignored -> new AtomicLong(0)).set(val.longValue());
    }

    @Override
    public void add(String key, Number val, Context ctx) {
        values.computeIfAbsent(key, ignored -> new AtomicLong(0)).addAndGet(val.longValue());
    }

    public long get(String key) {
        return Optional.ofNullable(values.get(key)).map(AtomicLong::get).orElse(0L);
    }

    @Override
    public Context createContext(Map<String, ?> properties) {
        return DUMMY_CONTEXT;
    }
}
