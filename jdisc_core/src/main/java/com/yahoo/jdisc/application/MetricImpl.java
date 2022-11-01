// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.application;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.yahoo.jdisc.Metric;

import java.util.Map;

/**
 * @author Simon Thoresen Hult
 */
class MetricImpl implements Metric {

    private final LocalConsumer consumer;

    @Inject
    public MetricImpl(Provider<MetricConsumer> provider) {
        consumer = new LocalConsumer(provider);
    }

    @Override
    public void set(String key, Number val, Context ctx) {
        MetricConsumer consumer = currentConsumer();
        if (consumer != null) {
            consumer.set(key, val, ctx);
        }
    }

    @Override
    public void add(String key, Number val, Context ctx) {
        MetricConsumer consumer = currentConsumer();
        if (consumer != null) {
            consumer.add(key, val, ctx);
        }
    }

    @Override
    public Context createContext(Map<String, ?> keys) {
        MetricConsumer consumer = currentConsumer();
        if (consumer == null) {
            return null;
        }
        return consumer.createContext(keys);
    }

    private MetricConsumer currentConsumer() {
        return Thread.currentThread() instanceof ContainerThread thread ? thread.consumer() : consumer.get();
    }

    private static class LocalConsumer extends ThreadLocal<MetricConsumer> {

        final Provider<MetricConsumer> factory;

        LocalConsumer(Provider<MetricConsumer> factory) {
            this.factory = factory;
        }

        @Override
        protected MetricConsumer initialValue() {
            return factory.get();
        }
    }
}
