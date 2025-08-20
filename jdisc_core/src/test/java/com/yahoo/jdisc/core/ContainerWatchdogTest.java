// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.core;

import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.test.TestDriver;
import com.yahoo.test.ManualClock;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author bjorncs
 */
public class ContainerWatchdogTest {

    @Test
    void watchdog_counts_stale_container() {
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        ManualClock clock = new ManualClock(Instant.EPOCH);
        DummyMetric metric = new DummyMetric();
        ContainerWatchdog watchdog = new ContainerWatchdog(clock, false);

        ActiveContainer containerInstance = new ActiveContainer(driver.newContainerBuilder());
        assertEquals(1, containerInstance.resourcePool().retainCount());

        watchdog.onContainerActivation(containerInstance);
        assertEquals(0, runMonitorStepAndGetStaleContainerCount(watchdog, metric));

        clock.advance(Duration.ofHours(1));
        watchdog.onContainerActivation(null);
        assertEquals(1, runMonitorStepAndGetStaleContainerCount(watchdog, metric));

        clock.advance(ContainerWatchdog.GRACE_PERIOD);
        assertEquals(1, runMonitorStepAndGetStaleContainerCount(watchdog, metric));
        assertEquals(1, containerInstance.resourcePool().retainCount());

        clock.advance(Duration.ofSeconds(1));
        assertEquals(0, runMonitorStepAndGetStaleContainerCount(watchdog, metric));
        assertEquals(0, containerInstance.resourcePool().retainCount());
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

