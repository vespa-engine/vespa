// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.metric;

import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.application.MetricConsumer;
import com.yahoo.jdisc.core.ActiveContainerStatistics;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertTrue;

public class MetricUpdaterTest {
    
    @Test
    public void testFreeMemory() throws InterruptedException {
        MetricConsumer consumer = Mockito.mock(MetricConsumer.class);
        MetricProvider provider = MetricProviders.newInstance(consumer);

        Metric metric = provider.get();
        MetricUpdater updater = new MetricUpdater(metric, Mockito.mock(ActiveContainerStatistics.class), 10);
        long start = System.currentTimeMillis();
        boolean updated = false;
        while (System.currentTimeMillis() - start < 60000 && !updated) {
            Thread.sleep(10);
            if (memoryMetricsUpdated(updater)) {
                updated = true;
            }
        }
        assertTrue(memoryMetricsUpdated(updater));
    }

    private boolean memoryMetricsUpdated(MetricUpdater updater) {
        return updater.getFreeMemory()>0 && updater.getTotalMemory()>0;
    }
}
