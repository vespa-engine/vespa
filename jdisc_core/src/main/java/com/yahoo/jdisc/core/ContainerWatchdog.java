// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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

    static final Duration GRACE_PERIOD = Duration.ofMinutes(30);
    static final Duration UPDATE_PERIOD = Duration.ofMinutes(5);

    private static final Logger log = Logger.getLogger(ContainerWatchdog.class.getName());

    private final Object monitor = new Object();
    private final List<DeactivatedContainer> deactivatedContainers = new LinkedList<>();
    private final ScheduledExecutorService scheduler;
    private final Clock clock;

    private ActiveContainer currentContainer;
    private Instant currentContainerActivationTime;
    private int numStaleContainers;

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
        scheduler.scheduleAtFixedRate(
                this::monitorDeactivatedContainers, UPDATE_PERIOD.getSeconds(), UPDATE_PERIOD.getSeconds(), TimeUnit.SECONDS);
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
                deactivatedContainers.add(
                        new DeactivatedContainer(currentContainer, currentContainerActivationTime, clock.instant()));
            }
            currentContainer = nextContainer;
            currentContainerActivationTime = clock.instant();
        }
    }

    void monitorDeactivatedContainers() {
        synchronized (monitor) {
            int numStaleContainer = 0;
            Iterator<DeactivatedContainer> iterator = deactivatedContainers.iterator();
            while (iterator.hasNext()) {
                DeactivatedContainer container = iterator.next();
                int refCount = container.instance.retainCount();
                if (refCount == 0) {
                    iterator.remove();
                    break;
                }
                if (isPastGracePeriod(container)) {
                    ++numStaleContainer;
                    log.warning(
                            String.format(
                                    "Deactivated container still alive: instance=%s, activated=%s, deactivated=%s, ref-count=%d",
                                    container.instance.toString(), container.timeActivated, container.timeDeactivated, refCount));
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
