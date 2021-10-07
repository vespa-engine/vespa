// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.core;

import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.statistics.ContainerWatchdogMetrics;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * A watchdog that monitors all deactivated {@link ActiveContainer} instances with the purpose of detecting stale containers.
 *
 * @author bjorncs
 */
class ContainerWatchdog implements ContainerWatchdogMetrics, AutoCloseable {

    static final Duration GRACE_PERIOD = Duration.ofMinutes(5);
    static final Duration UPDATE_PERIOD = Duration.ofMinutes(5);
    static final Duration CHECK_PERIOD = Duration.ofSeconds(1);

    private static final Logger log = Logger.getLogger(ContainerWatchdog.class.getName());

    private final Object monitor = new Object();
    private final List<DeactivatedContainer> deactivatedContainers = new LinkedList<>();
    private final ScheduledExecutorService scheduler;
    private final Clock clock;

    private ActiveContainer currentContainer;
    private Instant currentContainerActivationTime;
    private int numStaleContainers;
    private Instant lastLogTime;

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
        scheduler.scheduleAtFixedRate(this::monitorDeactivatedContainers, CHECK_PERIOD.getSeconds(), CHECK_PERIOD.getSeconds(), TimeUnit.SECONDS);
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
        scheduler.shutdownNow();
        scheduler.awaitTermination(1, TimeUnit.MINUTES);
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
