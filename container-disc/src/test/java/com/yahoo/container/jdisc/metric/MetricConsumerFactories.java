// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.metric;

import com.yahoo.container.jdisc.MetricConsumerFactory;
import com.yahoo.jdisc.application.MetricConsumer;
import org.mockito.Mockito;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Simon Thoresen Hult
 */
class MetricConsumerFactories {

    public static MetricConsumerFactory newSingleton(final MetricConsumer consumer) {
        return new MetricConsumerFactory() {

            @Override
            public MetricConsumer newInstance() {
                return consumer;
            }
        };
    }

    public static MetricConsumerFactory newCounter(final AtomicInteger counter) {
        return new MetricConsumerFactory() {

            @Override
            public MetricConsumer newInstance() {
                counter.incrementAndGet();
                return Mockito.mock(MetricConsumer.class);
            }
        };
    }
}
