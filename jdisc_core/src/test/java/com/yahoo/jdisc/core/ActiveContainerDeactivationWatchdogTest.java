package com.yahoo.jdisc.core;

import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.ResourceReference;
import com.yahoo.jdisc.statistics.ActiveContainerMetrics;
import com.yahoo.jdisc.test.TestDriver;
import com.yahoo.test.ManualClock;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.Executors;

import static org.junit.Assert.assertEquals;

/**
 * @author bjorncs
 */
public class ActiveContainerDeactivationWatchdogTest {

    @Test
    public void watchdog_counts_deactivated_containers() {
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        ManualClock clock = new ManualClock(Instant.now());
        ActiveContainerDeactivationWatchdog watchdog =
                new ActiveContainerDeactivationWatchdog(clock, Executors.newScheduledThreadPool(1));
        MockMetric metric = new MockMetric();

        ActiveContainer containerWithoutRetainedResources = new ActiveContainer(driver.newContainerBuilder());

        watchdog.onContainerActivation(containerWithoutRetainedResources);
        watchdog.emitMetrics(metric);
        assertEquals(0, metric.totalCount);
        assertEquals(0, metric.withRetainedReferencesCount);

        watchdog.onContainerActivation(null);
        containerWithoutRetainedResources.release();
        clock.advance(ActiveContainerDeactivationWatchdog.ACTIVE_CONTAINER_GRACE_PERIOD);
        watchdog.emitMetrics(metric);
        assertEquals(0, metric.totalCount);
        assertEquals(0, metric.withRetainedReferencesCount);

        clock.advance(Duration.ofSeconds(1));
        watchdog.emitMetrics(metric);
        assertEquals(1, metric.totalCount);
        assertEquals(0, metric.withRetainedReferencesCount);

        ActiveContainer containerWithRetainedResources = new ActiveContainer(driver.newContainerBuilder());
        try (ResourceReference ignoredRef = containerWithRetainedResources.refer()) {
            watchdog.onContainerActivation(containerWithRetainedResources);
            containerWithRetainedResources.release();
            watchdog.onContainerActivation(null);
            clock.advance(ActiveContainerDeactivationWatchdog.ACTIVE_CONTAINER_GRACE_PERIOD.plusSeconds(1));
            watchdog.emitMetrics(metric);
            assertEquals(2, metric.totalCount);
            assertEquals(1, metric.withRetainedReferencesCount);
        }

    }

    private static class MockMetric implements Metric {
        public int totalCount;
        public int withRetainedReferencesCount;

        @Override
        public void set(String key, Number val, Context ctx) {
            switch (key) {
                case ActiveContainerMetrics.TOTAL_DEACTIVATED_CONTAINERS:
                    totalCount = val.intValue();
                    break;
                case ActiveContainerMetrics.DEACTIVATED_CONTAINERS_WITH_RETAINED_REFERENCES:
                    withRetainedReferencesCount = val.intValue();
                    break;
                default:
                    throw new UnsupportedOperationException();
            }
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
