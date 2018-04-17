// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.core;

import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.test.TestDriver;
import com.yahoo.test.ManualClock;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * @author bjorncs
 */
public class ContainerWatchdogTest {

    @Test
    public void watchdog_counts_stale_container() {
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        ManualClock clock = new ManualClock(Instant.EPOCH);
        DummyMetric metric = new DummyMetric();
        ContainerWatchdog watchdog = new ContainerWatchdog(mock(ScheduledExecutorService.class), clock);

        ActiveContainer containerWithoutRetainedResources = new ActiveContainer(driver.newContainerBuilder());

        watchdog.onContainerActivation(containerWithoutRetainedResources);
        assertEquals(0, runMonitorStepAndGetStaleContainerCount(watchdog, metric));

        clock.advance(Duration.ofHours(1));
        watchdog.onContainerActivation(null);
        assertEquals(0, runMonitorStepAndGetStaleContainerCount(watchdog, metric));

        clock.advance(ContainerWatchdog.GRACE_PERIOD);
        assertEquals(0, runMonitorStepAndGetStaleContainerCount(watchdog, metric));

        clock.advance(Duration.ofSeconds(1));
        assertEquals(1, runMonitorStepAndGetStaleContainerCount(watchdog, metric));

        containerWithoutRetainedResources.release();
        assertEquals(0, runMonitorStepAndGetStaleContainerCount(watchdog, metric));
    }

    private static int runMonitorStepAndGetStaleContainerCount(ContainerWatchdog watchdog, DummyMetric metric) {
        watchdog.monitorDeactivatedContainers();
        watchdog.emitMetrics(metric);
        return metric.value;
    }

    private static class DummyMetric implements Metric {
        int value;

        @Override
        public void set(String key, Number val, Context ctx) {
            this.value = val.intValue();
        }

        @Override
        public void add(String key, Number val, Context ctx) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Context createContext(Map<String, ?> properties) {
            throw new UnsupportedOperationException();
        }
    }
}

