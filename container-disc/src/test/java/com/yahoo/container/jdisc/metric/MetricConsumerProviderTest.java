// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.metric;

import com.yahoo.jdisc.application.MetricConsumer;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

/**
 * @author Simon Thoresen Hult
 */
public class MetricConsumerProviderTest {

    @Test
    public void requireThatSingleConsumerIsNotDelegated() {
        MetricConsumer consumer = Mockito.mock(MetricConsumer.class);
        MetricConsumerProvider provider = MetricConsumerProviders.newSingletonFactories(consumer);
        assertSame(consumer, provider.newInstance());
    }

    @Test
    public void requireThatMultipleConsumersAreDelegated() {
        MetricConsumer foo = Mockito.mock(MetricConsumer.class);
        MetricConsumer bar = Mockito.mock(MetricConsumer.class);
        MetricConsumerProvider provider = MetricConsumerProviders.newSingletonFactories(foo, bar);
        MetricConsumer consumer = provider.newInstance();
        assertNotSame(foo, consumer);
        assertNotSame(bar, consumer);
        consumer.add("foo", 6, null);
        Mockito.verify(foo, Mockito.times(1)).add("foo", 6, null);
        Mockito.verify(bar, Mockito.times(1)).add("foo", 6, null);
    }

}
