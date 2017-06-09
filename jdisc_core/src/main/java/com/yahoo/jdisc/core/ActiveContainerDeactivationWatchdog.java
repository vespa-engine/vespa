// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.core;

import com.google.inject.Inject;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.statistics.ActiveContainerMetrics;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.WeakHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toList;

/**
 * A watchdog that monitors all deactivated {@link ActiveContainer} instances with the purpose of detecting containers
 * that are unable to be garbage collected by the JVM.
 *
 * @author bjorncs
 */
class ActiveContainerDeactivationWatchdog implements ActiveContainerMetrics, AutoCloseable {
    static final Duration WATCHDOG_FREQUENCY = Duration.ofMinutes(20);
    static final Duration ACTIVE_CONTAINER_GRACE_PERIOD = Duration.ofHours(1);
    static final Duration GC_TRIGGER_FREQUENCY = ACTIVE_CONTAINER_GRACE_PERIOD.minusMinutes(5);

    private static final Logger log = Logger.getLogger(ActiveContainerDeactivationWatchdog.class.getName());

    private final Object monitor = new Object();
    private final WeakHashMap<ActiveContainer, LifecycleStats> deactivatedContainers = new WeakHashMap<>();
    private final ScheduledExecutorService scheduler;
    private final Clock clock;

    private ActiveContainer currentContainer;
    private Instant currentContainerActivationTime;

    @Inject
    ActiveContainerDeactivationWatchdog() {
        this(
                Clock.systemUTC(),
                new ScheduledThreadPoolExecutor(1, runnable -> {
                    Thread thread = new Thread(runnable, "active-container-deactivation-watchdog");
                    thread.setDaemon(true);
                    return thread;
                }));
    }

    ActiveContainerDeactivationWatchdog(Clock clock, ScheduledExecutorService scheduler) {
        this.clock = clock;
        this.scheduler = scheduler;
        this.scheduler.scheduleWithFixedDelay(
                this::warnOnStaleContainers,
                WATCHDOG_FREQUENCY.getSeconds(),
                WATCHDOG_FREQUENCY.getSeconds(),
                TimeUnit.SECONDS);
        this.scheduler.scheduleWithFixedDelay(
                ActiveContainerDeactivationWatchdog::triggerGc,
                GC_TRIGGER_FREQUENCY.getSeconds(),
                GC_TRIGGER_FREQUENCY.getSeconds(),
                TimeUnit.SECONDS);
    }

    void onContainerActivation(ActiveContainer nextContainer) {
        synchronized (monitor) {
            Instant now = clock.instant();
            if (currentContainer != null) {
                deactivatedContainers.put(currentContainer, new LifecycleStats(currentContainerActivationTime, now));
            }
            currentContainer = nextContainer;
            currentContainerActivationTime = now;
        }
    }

    @Override
    public void emitMetrics(Metric metric) {
        List<DeactivatedContainer> snapshot = getDeactivatedContainersSnapshot();
        long containersWithRetainedRefsCount = snapshot.stream()
                .filter(c -> c.activeContainer.retainCount() > 0)
                .count();
        metric.set(TOTAL_DEACTIVATED_CONTAINERS, snapshot.size(), null);
        metric.set(DEACTIVATED_CONTAINERS_WITH_RETAINED_REFERENCES, containersWithRetainedRefsCount, null);
    }

    @Override
    public void close() {
        synchronized (monitor) {
            scheduler.shutdown();
            deactivatedContainers.clear();
            currentContainer = null;
            currentContainerActivationTime = null;
        }
    }

    private void warnOnStaleContainers() {
        try {
            List<DeactivatedContainer> snapshot = getDeactivatedContainersSnapshot();
            if (snapshot.isEmpty()) return;
            logWarning(snapshot);
        } catch (Throwable t) {
            log.log(Level.WARNING, "Watchdog task died!", t);
        }
    }

    private static void triggerGc() {
        // ActiveContainer has a finalizer, so gc -> finalizer -> gc is required.
        System.gc();
        System.runFinalization();
        System.gc();
    }

    private List<DeactivatedContainer> getDeactivatedContainersSnapshot() {
        Instant now = clock.instant();
        synchronized (monitor) {
            return deactivatedContainers.entrySet().stream()
                    .filter(e -> e.getValue().isPastGracePeriod(now))
                    .map(e -> new DeactivatedContainer(e.getKey(), e.getValue()))
                    .sorted(comparing(e -> e.lifecycleStats.timeActivated))
                    .collect(toList());
        }
    }

    private static void logWarning(List<DeactivatedContainer> snapshot) {
        log.warning(String.format("%s instances of deactivated containers are still alive.", snapshot.size()));
        for (DeactivatedContainer deactivatedContainer : snapshot) {
            log.warning(" - " + deactivatedContainer.toSummaryString());
        }
    }

    private static class LifecycleStats {
        public final Instant timeActivated;
        public final Instant timeDeactivated;

        public LifecycleStats(Instant timeActivated, Instant timeDeactivated) {
            this.timeActivated = timeActivated;
            this.timeDeactivated = timeDeactivated;
        }

        public boolean isPastGracePeriod(Instant instant) {
            return timeDeactivated.plus(ACTIVE_CONTAINER_GRACE_PERIOD).isBefore(instant);
        }
    }

    private static class DeactivatedContainer {
        public final ActiveContainer activeContainer;
        public final LifecycleStats lifecycleStats;

        public DeactivatedContainer(ActiveContainer activeContainer, LifecycleStats lifecycleStats) {
            this.activeContainer = activeContainer;
            this.lifecycleStats = lifecycleStats;
        }

        public String toSummaryString() {
            return String.format("%s: time activated = %s, time deactivated = %s, reference count = %d",
                    activeContainer.toString(),
                    lifecycleStats.timeActivated.toString(),
                    lifecycleStats.timeDeactivated.toString(),
                    activeContainer.retainCount());
        }
    }

}
