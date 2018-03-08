// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.core;

import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.ResourceReference;
import com.yahoo.jdisc.statistics.ActiveContainerMetrics;
import com.yahoo.jdisc.test.TestDriver;
import com.yahoo.test.ManualClock;
import org.junit.Ignore;
import org.junit.Test;

import java.lang.ref.WeakReference;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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
        clock.advance(ActiveContainerDeactivationWatchdog.REPORTING_GRACE_PERIOD);
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
            clock.advance(ActiveContainerDeactivationWatchdog.REPORTING_GRACE_PERIOD.plusSeconds(1));
            watchdog.emitMetrics(metric);
            assertEquals(2, metric.totalCount);
            assertEquals(1, metric.withRetainedReferencesCount);
        }

    }

    @Test
    @Ignore("Ignored as it assumes phantom references are enqueued right after first GC have cleared the weak reference. " +
            "This is the case on most JVMs.")
    public void deactivated_container_destructed_if_its_reference_counter_is_nonzero() {
        ExecutorMock executor = new ExecutorMock();
        ManualClock clock = new ManualClock(Instant.now());
        ActiveContainerDeactivationWatchdog watchdog = new ActiveContainerDeactivationWatchdog(clock, executor);
        ActiveContainer container =
                new ActiveContainer(TestDriver.newSimpleApplicationInstanceWithoutOsgi().newContainerBuilder());
        AtomicBoolean destructed = new AtomicBoolean(false);
        container.shutdown().notifyTermination(() -> destructed.set(true));

        container.refer(); // increase reference counter to simulate a leaking resource
        watchdog.onContainerActivation(container);
        container.release(); // release resource
        watchdog.onContainerActivation(null); // deactivate container

        WeakReference<ActiveContainer> containerWeakReference = new WeakReference<>(container);
        container = null; // make container instance collectable by GC
        clock.advance(ActiveContainerDeactivationWatchdog.GC_GRACE_PERIOD.plusSeconds(1));

        executor.triggerGcCommand.run();

        assertNull("Container is not GCed - probably because the watchdog has a concrete reference to it",
                   containerWeakReference.get());

        executor.enforceDestructionOfGarbageCollectedContainersCommand.run();

        assertTrue("Destructor is not called on deactivated container", destructed.get());
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

    private static class ExecutorMock extends ScheduledThreadPoolExecutor {

        public Runnable warnOnStaleContainersCommand;
        public Runnable triggerGcCommand;
        public Runnable enforceDestructionOfGarbageCollectedContainersCommand;
        private int registrationCounter = 0;

        public ExecutorMock() {
            super(1);
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
            if (registrationCounter == 0) {
                warnOnStaleContainersCommand = command;
            } else if (registrationCounter == 1) {
                triggerGcCommand = command;
            } else if (registrationCounter == 2) {
                enforceDestructionOfGarbageCollectedContainersCommand = command;
            } else {
                throw new IllegalStateException("Unexpected registration");
            }
            ++registrationCounter;
            return null;
        }

    }

}
