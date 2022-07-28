// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.metric;

import com.yahoo.container.di.componentgraph.Provider;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.application.MetricConsumer;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Simon Thoresen Hult
 */
public class MetricProviderTest {

    @Test
    void requireThatMetricProviderDelegatesToConsumerFactory() {
        MetricConsumer consumer = Mockito.mock(MetricConsumer.class);
        MetricProvider provider = MetricProviders.newInstance(consumer);

        Metric metric = provider.get();
        assertNotNull(metric);

        Metric.Context fooCtx = Mockito.mock(Metric.Context.class);
        metric.add("foo", 6, fooCtx);
        metric.set("foo", 9, fooCtx);
        Mockito.verify(consumer, Mockito.times(1)).add("foo", 6, fooCtx);
        Mockito.verify(consumer, Mockito.times(1)).set("foo", 9, fooCtx);
    }

    @Test
    void requireThatThreadLocalConsumersAreProvided() throws Exception {
        AtomicInteger cnt = new AtomicInteger(0);
        final MetricProvider metricProvider = MetricProviders.newInstance(MetricConsumerFactories.newCounter(cnt));
        assertEquals(0, cnt.get());
        metricProvider.get().add("foo", 6, null); // need to call on Metric to instantiate MetricConsumer
        assertEquals(1, cnt.get());
        metricProvider.get().add("bar", 9, null);
        assertEquals(1, cnt.get());

        ExecutorService executor = Executors.newSingleThreadExecutor();
        assertTrue(executor.submit(new MetricTask(metricProvider)).get(60, TimeUnit.SECONDS));
        assertEquals(2, cnt.get());
        assertTrue(executor.submit(new MetricTask(metricProvider)).get(60, TimeUnit.SECONDS));
        assertEquals(2, cnt.get());
    }

    private static class MetricTask implements Callable<Boolean> {

        final Provider<Metric> provider;

        MetricTask(Provider<Metric> provider) {
            this.provider = provider;
        }

        @Override
        public Boolean call() throws Exception {
            provider.get().add("foo", 69, null);
            return true;
        }
    }
}
