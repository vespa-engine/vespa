// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.metric;

import com.yahoo.container.jdisc.MetricConsumerFactory;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.application.MetricConsumer;

import java.util.Map;

/**
 * <p>If more than one {@link MetricConsumerFactory} is registered in a container, calls to <code>Metric</code> need to be
 * forwarded to all the underlying <code>MetricConsumers</code>. That is the responsibility of this class. Instances of this
 * class is created by the {@link MetricConsumerProvider} in those cases.</p>
 *
 * @author Simon Thoresen Hult
 */
public final class ForwardingMetricConsumer implements MetricConsumer {

    private final MetricConsumer[] consumers;

    public ForwardingMetricConsumer(MetricConsumer[] consumers) {
        this.consumers = consumers;
    }

    @Override
    public void set(String key, Number val, Metric.Context ctx) {
        ForwardingContext fwd = (ForwardingContext)ctx;
        for (int i = 0; i < consumers.length; ++i) {
            consumers[i].set(key, val, fwd != null ? fwd.contexts[i] : null);
        }
    }

    @Override
    public void add(String key, Number val, Metric.Context ctx) {
        ForwardingContext fwd = (ForwardingContext)ctx;
        for (int i = 0; i < consumers.length; ++i) {
            consumers[i].add(key, val, fwd != null ? fwd.contexts[i] : null);
        }
    }

    @Override
    public Metric.Context createContext(Map<String, ?> properties) {
        Metric.Context[] contexts = new Metric.Context[consumers.length];
        for (int i = 0; i < consumers.length; ++i) {
            contexts[i] = consumers[i].createContext(properties);
        }
        return new ForwardingContext(contexts);
    }

    private static class ForwardingContext implements Metric.Context {

        final Metric.Context[] contexts;

        ForwardingContext(Metric.Context[] contexts) {
            this.contexts = contexts;
        }
    }

}
