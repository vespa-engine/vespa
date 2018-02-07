// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.core;

import com.google.inject.Inject;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.statistics.ActiveContainerMetrics;

import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
// TODO Rewrite to use cleaners instead of phantom references after Java 9 migration
class ActiveContainerDeactivationWatchdog implements ActiveContainerMetrics, AutoCloseable {
    static final Duration WATCHDOG_FREQUENCY = Duration.ofMinutes(20);
    static final Duration ACTIVE_CONTAINER_GRACE_PERIOD = Duration.ofHours(4);
    static final Duration GC_TRIGGER_FREQUENCY = Duration.ofHours(1); // Must be a fraction of ACTIVE_CONTAINER_GRACE_PERIOD
    static final Duration ENFORCE_DESTRUCTION_GCED_CONTAINERS_FREQUENCY = Duration.ofMinutes(5);
    static final int DEFAULT_DEACTIVATED_CONTAINERS_BEFORE_GC_THRESHOLD = 4;
    private static final Logger log = Logger.getLogger(ActiveContainerDeactivationWatchdog.class.getName());

    private final Object monitor = new Object();
    private final WeakHashMap<ActiveContainer, LifecycleStats> deactivatedContainers = new WeakHashMap<>();
    private final ReferenceQueue<ActiveContainer> garbageCollectedContainers = new ReferenceQueue<>();
    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    // Instances of the phantom references must be kept alive until they are polled from the reference queue
    private final Set<ActiveContainerPhantomReference> destructorReferences = new HashSet<>();
    private final ScheduledExecutorService scheduler;
    private final Clock clock;
    private final int deactivatedContainersBeforeGcThreshold;

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
                }),
                DEFAULT_DEACTIVATED_CONTAINERS_BEFORE_GC_THRESHOLD);
    }

    ActiveContainerDeactivationWatchdog(Clock clock,
                                        ScheduledExecutorService scheduler,
                                        int deactivatedContainersBeforeGcThreshold) {
        this.clock = clock;
        this.scheduler = scheduler;
        // NOTE: Make sure to update the unit test if the order commands are registered is changed.
        this.scheduler.scheduleAtFixedRate(this::warnOnStaleContainers,
                                           WATCHDOG_FREQUENCY.getSeconds(),
                                           WATCHDOG_FREQUENCY.getSeconds(),
                                           TimeUnit.SECONDS);
        this.scheduler.scheduleAtFixedRate(this::triggerGc,
                                           GC_TRIGGER_FREQUENCY.getSeconds(),
                                           GC_TRIGGER_FREQUENCY.getSeconds(),
                                           TimeUnit.SECONDS);
        this.scheduler.scheduleAtFixedRate(this::enforceDestructionOfGarbageCollectedContainers,
                                           ENFORCE_DESTRUCTION_GCED_CONTAINERS_FREQUENCY.getSeconds(),
                                           ENFORCE_DESTRUCTION_GCED_CONTAINERS_FREQUENCY.getSeconds(),
                                           TimeUnit.SECONDS);
        this.deactivatedContainersBeforeGcThreshold = deactivatedContainersBeforeGcThreshold;
    }

    void onContainerActivation(ActiveContainer nextContainer) {
        synchronized (monitor) {
            Instant now = clock.instant();
            ActiveContainer previousContainer = currentContainer;
            currentContainer = nextContainer;
            currentContainerActivationTime = now;
            if (previousContainer != null) {
                deactivatedContainers.put(previousContainer, new LifecycleStats(currentContainerActivationTime, now));
                destructorReferences.add(new ActiveContainerPhantomReference(previousContainer, garbageCollectedContainers));
            }
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
            destructorReferences.clear();
            currentContainer = null;
            currentContainerActivationTime = null;
        }
    }

    private void warnOnStaleContainers() {
        log.log(Level.FINE, "Checking for stale containers");
        try {
            List<DeactivatedContainer> snapshot = getDeactivatedContainersSnapshot();
            if (snapshot.isEmpty()) return;
            logWarning(snapshot);
        } catch (Throwable t) {
            log.log(Level.WARNING, "Watchdog task died!", t);
        }
    }

    private void triggerGc() {
        int deactivatedContainers = getDeactivatedContainersSnapshot().size();
        boolean shouldGc = deactivatedContainers > deactivatedContainersBeforeGcThreshold;
        if (!shouldGc) return;
        log.log(Level.FINE, String.format("Triggering GC (currently %d deactivated containers still alive)", deactivatedContainers));
        System.gc();
        System.runFinalization();
    }

    private void enforceDestructionOfGarbageCollectedContainers() {
        log.log(Level.FINE, "Enforcing destruction of GCed containers");
        ActiveContainerPhantomReference reference;
        while ((reference = (ActiveContainerPhantomReference) garbageCollectedContainers.poll()) != null) {
            try {
                reference.enforceDestruction();
            } catch (Throwable t) {
                log.log(Level.SEVERE, "Failed to do post-GC destruction of " + reference.containerName, t);
            } finally {
                destructorReferences.remove(reference);
                reference.clear();
            }
        }
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

    private static class ActiveContainerPhantomReference extends PhantomReference<ActiveContainer> {
        public final String containerName;
        private final ActiveContainer.Destructor destructor;

        public ActiveContainerPhantomReference(ActiveContainer activeContainer,
                                               ReferenceQueue<? super ActiveContainer> q) {
            super(activeContainer, q);
            this.containerName = activeContainer.toString();
            this.destructor = activeContainer.destructor;
        }

        public void enforceDestruction() {
            boolean alreadyCompleted = destructor.destruct();
            if (!alreadyCompleted) {
                log.severe(containerName + " was not correctly cleaned up " +
                        "because of a resource leak or invalid use of reference counting.");
            }
        }
    }
}
