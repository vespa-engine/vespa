// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.core;

import com.yahoo.jdisc.Metric;

import java.time.Instant;
import java.util.List;
import java.util.WeakHashMap;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

/**
 * Tracks statistics on stale {@link ActiveContainer} instances.
 *
 * @author bjorncs
 */
public class ActiveContainerStatistics {
    public interface Metrics {
        String TOTAL_DEACTIVATED_CONTAINERS = "jdisc.deactivated_containers.total";
        String DEACTIVATED_CONTAINERS_WITH_RETAINED_REFERENCES = "jdisc.deactivated_containers.with_retained_refs";
    }

    private static final Logger log = Logger.getLogger(ActiveContainerStatistics.class.getName());

    private final WeakHashMap<ActiveContainer, ActiveContainerStats> activeContainers = new WeakHashMap<>();
    private final Object lock = new Object();

    public void onActivated(ActiveContainer activeContainer) {
        synchronized (lock) {
            activeContainers.put(activeContainer, new ActiveContainerStats(Instant.now()));
        }
    }

    public void onDeactivated(ActiveContainer activeContainer) {
        synchronized (lock) {
            ActiveContainerStats containerStats = activeContainers.get(activeContainer);
            if (containerStats == null) {
                throw new IllegalStateException("onActivated() has not been called for container: " + activeContainer);
            }
            containerStats.setTimeDeactived(Instant.now());
        }
    }

    public void outputMetrics(Metric metric) {
        synchronized (lock) {
            DeactivatedContainerMetrics metrics = deactivatedContainerStream()
                    .collect(
                            DeactivatedContainerMetrics::new,
                            DeactivatedContainerMetrics::aggregate,
                            DeactivatedContainerMetrics::merge);

            metric.set(Metrics.TOTAL_DEACTIVATED_CONTAINERS, metrics.deactivatedContainerCount, null);
            metric.set(Metrics.DEACTIVATED_CONTAINERS_WITH_RETAINED_REFERENCES, metrics.deactivatedContainersWithRetainedRefsCount, null);
        }
    }

    public void printSummaryToLog() {
        synchronized (lock) {
            List<DeactivatedContainer> deactivatedContainers = deactivatedContainerStream().collect(toList());
            if (deactivatedContainers.isEmpty()) {
                return;
            }
            log.warning(
                    "Multiple instances of ActiveContainer leaked! " + deactivatedContainers.size() +
                            " instances are still present.");
            deactivatedContainers.stream()
                    .map(c -> " - " + c.toSummaryString())
                    .forEach(log::warning);
        }
    }

    private Stream<DeactivatedContainer> deactivatedContainerStream() {
        synchronized (lock) {
            return activeContainers.entrySet().stream()
                    .filter(e -> e.getKey() != null)
                    .filter(e -> !e.getValue().isDeactivated())
                    .map(e -> new DeactivatedContainer(e.getKey(), e.getValue().timeActivated, e.getValue().timeDeactived));
        }
    }

    private static class ActiveContainerStats {
        public final Instant timeActivated;
        public Instant timeDeactived;

        public ActiveContainerStats(Instant timeActivated) {
            this.timeActivated = timeActivated;
        }

        public void setTimeDeactived(Instant instant) {
            this.timeDeactived = instant;
        }

        public boolean isDeactivated() {
            return timeDeactived == null;
        }
    }

    private static class DeactivatedContainer {
        public final ActiveContainer activeContainer;
        public final Instant timeActivated;
        public final Instant timeDeactivated;

        public DeactivatedContainer(ActiveContainer activeContainer, Instant timeActivated, Instant timeDeactivated) {
            this.activeContainer = activeContainer;
            this.timeActivated = timeActivated;
            this.timeDeactivated = timeDeactivated;
        }

        public String toSummaryString() {
            return String.format("%s: timeActivated=%s, timeDeactivated=%s, retainCount=%d",
                    activeContainer.toString(),
                    timeActivated.toString(),
                    timeDeactivated.toString(),
                    activeContainer.retainCount());
        }
    }

    private static class DeactivatedContainerMetrics {
        public int deactivatedContainerCount = 0;
        public int deactivatedContainersWithRetainedRefsCount = 0;

        public void aggregate(DeactivatedContainer deactivatedContainer) {
            ++deactivatedContainerCount;
            if (deactivatedContainer.activeContainer.retainCount() > 0) {
                ++deactivatedContainersWithRetainedRefsCount;
            }
        }

        public DeactivatedContainerMetrics merge(DeactivatedContainerMetrics other) {
            deactivatedContainerCount += other.deactivatedContainerCount;
            deactivatedContainersWithRetainedRefsCount += other.deactivatedContainersWithRetainedRefsCount;
            return this;
        }
    }
}
