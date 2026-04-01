// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.core;

import com.yahoo.concurrent.Threads;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.statistics.DeactivatedContainerWatchdogMetrics;
import com.yahoo.lang.MutableInteger;
import com.yahoo.text.Text;
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
class DeactivatedContainerWatchdog implements DeactivatedContainerWatchdogMetrics, AutoCloseable {

    static final Duration GRACE_PERIOD = Duration.ofMinutes(30);
    static final Duration CONTAINER_CHECK_PERIOD = Duration.ofMinutes(1);

    private static final Logger log = Logger.getLogger(DeactivatedContainerWatchdog.class.getName());

    private final Object monitor = new Object();
    private final List<DeactivatedContainer> deactivatedContainers = new LinkedList<>();
    private final Clock clock;
    private final boolean enableScheduler;

    private ScheduledExecutorService scheduler;
    private ActiveContainer currentContainer;
    private Instant currentContainerActivationTime;
    private ScheduledFuture<?> threadMonitoringTask;
    private ScheduledFuture<?> containerMontoringTask;


    DeactivatedContainerWatchdog() {
        this(Clock.systemUTC(), true);
    }

    /* For unit testing only */
    DeactivatedContainerWatchdog(Clock clock, boolean enableScheduler) {
        this.clock = clock;
        this.enableScheduler = enableScheduler;
    }

    void start() {
        if (enableScheduler) {
            if (scheduler == null) this.scheduler = new ScheduledThreadPoolExecutor(
                    1,
                    runnable -> {
                        Thread thread = new Thread(runnable, "container-watchdog");
                        thread.setDaemon(true);
                        return thread;
                    });
            if (containerMontoringTask != null) containerMontoringTask.cancel(false);
            if (threadMonitoringTask != null) threadMonitoringTask.cancel(false);
            containerMontoringTask = scheduler.scheduleAtFixedRate(
                    this::monitorDeactivatedContainers,
                    CONTAINER_CHECK_PERIOD.getSeconds(), CONTAINER_CHECK_PERIOD.getSeconds(), TimeUnit.SECONDS);
        }
    }

    @Override
    public void emitMetrics(Metric metric) {
        int numStaleContainers;
        synchronized (monitor) {
            numStaleContainers = deactivatedContainers.size();
        }
        metric.set(TOTAL_DEACTIVATED_CONTAINERS, numStaleContainers, null);
    }

    @Override
    public void close() throws InterruptedException {
        if (containerMontoringTask != null) containerMontoringTask.cancel(false);
        if (threadMonitoringTask != null) threadMonitoringTask.cancel(false);
        synchronized (monitor) {
            if (scheduler != null) scheduler.shutdownNow();
            deactivatedContainers.clear();
            currentContainer = null;
            currentContainerActivationTime = null;
            if (scheduler != null && !scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                log.warning("Failed to shutdown container watchdog within 10 seconds");
            }
            scheduler = null;
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
            if (enableScheduler) threadMonitoringTask = scheduler.schedule(this::monitorThreads, 1, TimeUnit.MINUTES);
        }
    }

    private String removalMsg(DeactivatedContainer container) {
        return Text.format(
                "Removing deactivated container as all references are released: instance=%s, activated=%s, deactivated=%s",
                container.instance, container.timeActivated, container.timeDeactivated);
    }

    private String regularMsg(DeactivatedContainer container, int refCount) {
        return Text.format(
                "Deactivated container still alive: instance=%s, activated=%s, deactivated=%s, ref-count=%d",
                container.instance, container.timeActivated, container.timeDeactivated, refCount);
    }

    void monitorDeactivatedContainers() {
        synchronized (monitor) {
            Iterator<DeactivatedContainer> iterator = deactivatedContainers.iterator();
            while (iterator.hasNext()) {
                DeactivatedContainer container = iterator.next();
                int refCount = container.instance.retainCount();
                if (refCount == 0) {
                    log.fine(() -> removalMsg(container));
                    iterator.remove();
                } else if (isPastGracePeriod(container)) {
                    log.info(() ->
                            Text.format(
                                    "Destroying stale deactivated container: instance=%s, activated=%s, deactivated=%s, ref-count=%d",
                                    container.instance, container.timeActivated, container.timeDeactivated, refCount));
                    // Destroying the container even though there are requests or other entities still having references to the server.
                    // Since it's way past the typical operation timeout, it's a big chance that the entity will not progress anymore.
                    // If that should happen though, it may lead to spurious exceptions if the container is used after it being destroyed.
                    //
                    // The benefits of explicit destruction:
                    // 1) Frees up resources that were held by the container.
                    // 2) Less nagging in logs about an issue that is extremely difficult for an end-user to debug.
                    //
                    // Downsides:
                    // 1) It's harder to debug the cause of a stale container instance,
                    //    as a JVM heap dump will not contain the smoking gun unless it's created within the grace period.
                    try {
                        container.instance.destroy();
                    } catch (Exception e) {
                        log.log(Level.INFO, e, () -> "Exception thrown while destroying stale deactivated container");
                    }
                    iterator.remove();
                } else {
                    log.fine(() -> regularMsg(container, refCount));
                }
            }
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
    @SuppressWarnings("deprecation") // Thread.getId() is deprecated
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
            b = getTargetFieldOfThread(t).flatMap(DeactivatedContainerWatchdog::hasClassloaderForUninstalledBundle).orElse(null);
            if (b != null) {
                staleThreads.add(new ThreadDetails(t, b));
            }
            // Note: no reliable mechanism for detecting threads owned by leaked executors (e.g. ScheduledThreadPoolExecutor)
        }
        if (!staleThreads.isEmpty()) {
            StringBuilder msg = new StringBuilder(
                    Text.format(
                            "Found %d stale threads that should have been stopped during previous reconfiguration(s). " +
                            "These threads have a classloader for a bundle that has been uninstalled: \n",
                            staleThreads.size()));
            MutableInteger i = new MutableInteger(1);
            Comparator<ThreadDetails> outputOrdering =
                    Comparator.<ThreadDetails, Long>comparing(td -> td.bundle().getBundleId())
                    .thenComparing(td -> td.thread().getName()).thenComparing(td -> td.thread().getId());
            staleThreads.stream().sorted(outputOrdering).forEach(t ->
                    msg.append(Text.format("%d) Thread '%s' using bundle '%s'. \n",
                            i.next(), t.thread().getName(), t.bundle().toString())));
            log.log(Level.INFO, msg::toString);  // Level 'info' until deemed reliable enough as 'warning'
        }
    }

    private static Optional<Runnable> getTargetFieldOfThread(Thread t) {
        try {
            // Get the Runnable from inside Java 17's Thread object:
            Field f = Thread.class.getDeclaredField("target");
            f.setAccessible(true);
            return Optional.ofNullable((Runnable)f.get(t));
        } catch (ReflectiveOperationException e) {
        }
        try {
            // Get the Runnable from inside Java 21's Thread object;
            // it's stored inside a "FieldHolder" internal class:
            Field holderField = Thread.class.getDeclaredField("holder");
            holderField.setAccessible(true);
            var holder = holderField.get(t);
            if (holder != null) {
                Field taskField = holder.getClass().getDeclaredField("task");
                taskField.setAccessible(true);
                var task = taskField.get(holder);
                return Optional.ofNullable((Runnable)task);
            }
        } catch (ReflectiveOperationException e) {
        }
        return Optional.empty();
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
