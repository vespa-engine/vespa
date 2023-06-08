// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.core;

import com.yahoo.concurrent.Threads;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.statistics.ContainerWatchdogMetrics;
import com.yahoo.lang.MutableInteger;
import org.apache.felix.framework.BundleWiringImpl;
import org.osgi.framework.Bundle;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A watchdog that monitors all deactivated {@link ActiveContainer} instances with the purpose of detecting stale containers.
 *
 * @author bjorncs
 */
class ContainerWatchdog implements ContainerWatchdogMetrics, AutoCloseable {

    static final Duration GRACE_PERIOD = Duration.ofMinutes(5);
    static final Duration UPDATE_PERIOD = Duration.ofMinutes(5);
    static final Duration CONTAINER_CHECK_PERIOD = Duration.ofSeconds(1);

    private static final Logger log = Logger.getLogger(ContainerWatchdog.class.getName());

    private final Object monitor = new Object();
    private final List<DeactivatedContainer> deactivatedContainers = new LinkedList<>();
    private final ScheduledExecutorService scheduler;
    private final Clock clock;

    private ActiveContainer currentContainer;
    private Instant currentContainerActivationTime;
    private int numStaleContainers;
    private Instant lastLogTime;
    private ScheduledFuture<?> threadMonitoringTask;
    private ScheduledFuture<?> containerMontoringTask;


    ContainerWatchdog() {
        this(new ScheduledThreadPoolExecutor(
                     1,
                     runnable -> {
                         Thread thread = new Thread(runnable, "container-watchdog");
                         thread.setDaemon(true);
                         return thread;
                     }),
             Clock.systemUTC());
    }

    ContainerWatchdog(ScheduledExecutorService scheduler, Clock clock) {
        this.scheduler = scheduler;
        this.clock = clock;
        this.lastLogTime = clock.instant();
    }

    void start() {
        this.containerMontoringTask = scheduler.scheduleAtFixedRate(this::monitorDeactivatedContainers,
                CONTAINER_CHECK_PERIOD.getSeconds(), CONTAINER_CHECK_PERIOD.getSeconds(), TimeUnit.SECONDS);
    }

    @Override
    public void emitMetrics(Metric metric) {
        int numStaleContainers;
        synchronized (monitor) {
            numStaleContainers = this.numStaleContainers;
        }
        metric.set(TOTAL_DEACTIVATED_CONTAINERS, numStaleContainers, null);
    }

    @Override
    public void close() throws InterruptedException {
        if (containerMontoringTask != null) containerMontoringTask.cancel(false);
        if (threadMonitoringTask != null) threadMonitoringTask.cancel(false);
        synchronized (monitor) {
            deactivatedContainers.clear();
            currentContainer = null;
            currentContainerActivationTime = null;
        }
    }

    void onContainerActivation(ActiveContainer nextContainer) {
        synchronized (monitor) {
            if (currentContainer != null) {
                deactivatedContainers.add(new DeactivatedContainer(currentContainer, currentContainerActivationTime, clock.instant()));
            }
            currentContainer = nextContainer;
            currentContainerActivationTime = clock.instant();
            if (threadMonitoringTask != null) threadMonitoringTask.cancel(false);
            threadMonitoringTask = scheduler.schedule(this::monitorThreads, 1, TimeUnit.MINUTES);
        }
    }

    private String removalMsg(DeactivatedContainer container) {
        return String.format("Removing deactivated container: instance=%s, activated=%s, deactivated=%s",
                               container.instance, container.timeActivated, container.timeDeactivated);
    }

    private String regularMsg(DeactivatedContainer container, int refCount) {
        return String.format(
                "Deactivated container still alive: instance=%s, activated=%s, deactivated=%s, ref-count=%d",
                container.instance, container.timeActivated, container.timeDeactivated, refCount);
    }

    void monitorDeactivatedContainers() {
        synchronized (monitor) {
            int numStaleContainer = 0;
            Iterator<DeactivatedContainer> iterator = deactivatedContainers.iterator();
            boolean timeToLogAgain = clock.instant().isAfter(lastLogTime.plus(UPDATE_PERIOD));
            while (iterator.hasNext()) {
                DeactivatedContainer container = iterator.next();
                int refCount = container.instance.retainCount();
                if (refCount == 0) {
                    log.fine(removalMsg(container));
                    iterator.remove();
                } else {
                    if (isPastGracePeriod(container)) {
                        ++numStaleContainer;
                        if (timeToLogAgain) {
                            log.warning(regularMsg(container, refCount));
                            lastLogTime = clock.instant();
                        }
                    }
                }
            }
            this.numStaleContainers = numStaleContainer;
        }
    }

    private boolean isPastGracePeriod(DeactivatedContainer container) {
        return clock.instant().isAfter(container.timeDeactivated.plus(GRACE_PERIOD));
    }

    private void monitorThreads() {
        Collection<Thread> threads = Threads.getAllThreads();
        warnOnThreadsHavingClassloaderFromUninstalledBundles(threads);
    }

    /**
     * Detect and warn on threads leaked by defunct application components.
     * These are threads launched by components of application bundles and not stopped after subsequent component deconstruction.
     * The below algorithm is a heuristic and will not detect all leaked threads.
     */
    private void warnOnThreadsHavingClassloaderFromUninstalledBundles(Collection<Thread> threads) {
        record ThreadDetails(Thread thread, Bundle bundle) {}
        List<ThreadDetails> staleThreads = new ArrayList<>();
        for (Thread t : threads) {
            // Find threads with context classloader from an uninstalled bundle
            Bundle b = isClassloaderForUninstalledBundle(t.getContextClassLoader()).orElse(null);
            if (b != null) {
                staleThreads.add(new ThreadDetails(t, b));
                continue;
            }

            // Find threads which are sub-classes of java.lang.Thread from an uninstalled bundle
            b = hasClassloaderForUninstalledBundle(t).orElse(null);
            if (b != null) {
                staleThreads.add(new ThreadDetails(t, b));
                continue;
            }
            // Find threads which Runnable is a class from an uninstalled bundle
            // This may create false positives
            b = getTargetFieldOfThread(t).flatMap(ContainerWatchdog::hasClassloaderForUninstalledBundle).orElse(null);
            if (b != null) {
                staleThreads.add(new ThreadDetails(t, b));
            }
            // Note: no reliable mechanism for detecting threads owned by leaked executors (e.g. ScheduledThreadPoolExecutor)
        }
        if (!staleThreads.isEmpty()) {
            StringBuilder msg = new StringBuilder(
                    ("Found %d stale threads that should have been stopped during previous reconfiguration(s). " +
                            "These threads have a classloader for a bundle that has been uninstalled: \n")
                            .formatted(staleThreads.size()));
            MutableInteger i = new MutableInteger(1);
            Comparator<ThreadDetails> outputOrdering =
                    Comparator.<ThreadDetails, Long>comparing(td -> td.bundle().getBundleId())
                    .thenComparing(td -> td.thread().getName()).thenComparing(td -> td.thread().getId());
            staleThreads.stream().sorted(outputOrdering).forEach(t ->
                    msg.append("%d) Thread '%s' using bundle '%s'. \n"
                            .formatted(i.next(), t.thread().getName(), t.bundle().toString())));
            log.log(Level.INFO, msg::toString);  // Level 'info' until deemed reliable enough as 'warning'
        }
    }

    private static Optional<Runnable> getTargetFieldOfThread(Thread t) {
        try {
            Field f = Thread.class.getDeclaredField("target");
            f.setAccessible(true);
            return Optional.ofNullable((Runnable)f.get(t));
        } catch (ReflectiveOperationException e) {
            return Optional.empty();
        }
    }

    private static Optional<Bundle> hasClassloaderForUninstalledBundle(Object o) {
        return isClassloaderForUninstalledBundle(o.getClass().getClassLoader());
    }

    private static Optional<Bundle> isClassloaderForUninstalledBundle(ClassLoader cl) {
        if (cl instanceof BundleWiringImpl.BundleClassLoader bcl) {
            Bundle b = bcl.getBundle();
            if (b.getState() == Bundle.UNINSTALLED) return Optional.of(b);
        }
        return Optional.empty();
    }

    private static class DeactivatedContainer {
        final ActiveContainer instance;
        final Instant timeActivated;
        final Instant timeDeactivated;

        DeactivatedContainer(ActiveContainer instance, Instant timeActivated, Instant timeDeactivated) {
            this.instance = instance;
            this.timeActivated = timeActivated;
            this.timeDeactivated = timeDeactivated;
        }
    }
}
