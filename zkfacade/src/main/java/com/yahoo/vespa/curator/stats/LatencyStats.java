// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.curator.stats;

import java.time.Duration;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Optional;
import java.util.function.LongSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.lang.Math.max;
import static java.lang.Math.round;

/**
 * An instance of {@code LatencyStats} keeps track of statistics related to <em>time intervals</em> that
 * start at a particular moment in time and ends at a later time.  A typical example is the processing of
 * requests:  Each newly received request starts a new interval, and ends when the response is sent.
 *
 * <p>The statistics only applies to the current <em>time period</em>, and can be retrieved as a
 * {@link LatencyMetrics} instance from e.g. {@link #getLatencyMetrics()}.  This fits well with how Yamas
 * works:  it collects metrics since last collection every minute or so.</p>
 *
 * @see LatencyMetrics
 * @author hakon
 */
// @Thread-Safe
public class LatencyStats {

    private static Logger logger = Logger.getLogger(LatencyStats.class.getName());

    private final LongSupplier nanoTimeSupplier;

    // NB: Keep these in sync with resetForNewPeriod()
    private final Object monitor = new Object();
    private long startOfPeriodNanos;
    private long endOfPeriodNanos;
    private double cumulativeLoadNanos;
    private Duration cumulativeLatency;
    private Duration maxLatency;
    private int numIntervalsStarted;
    private int numIntervalsEnded;
    private final HashSet<ActiveIntervalInfo> activeIntervals = new HashSet<>();
    private int maxLoad;

    /** Creates an empty LatencyStats starting the first time period now. */
    public LatencyStats() { this(System::nanoTime); }

    LatencyStats(LongSupplier nanoTimeSupplier) {
        this.nanoTimeSupplier = nanoTimeSupplier;
        this.endOfPeriodNanos = nanoTimeSupplier.getAsLong();
        resetForNewPeriod();
    }

    /** @see #startNewInterval() */
    public interface ActiveInterval extends AutoCloseable {
        @Override void close();
    }

    /**
     * Starts a new (active) interval.  The caller MUST call {@link ActiveInterval#close()} on the
     * returned instance exactly once, which will end the interval.
     */
    public ActiveInterval startNewInterval() {
        synchronized (monitor) {
            pushEndOfPeriodToNow();
            ActiveIntervalInfo activeIntervalInfo = new ActiveIntervalInfo(endOfPeriodNanos);
            activeIntervals.add(activeIntervalInfo);
            maxLoad = max(maxLoad, activeIntervals.size()) ;
            ++numIntervalsStarted;
            return () -> endInterval(activeIntervalInfo);
        }
    }

    /** Returns the metrics for the current time period up to now. */
    public LatencyMetrics getLatencyMetrics() {
        synchronized (monitor) {
            pushEndOfPeriodToNow();
            return makeLatencyMetrics();
        }
    }

    /** Returns the metrics for the current time period up to now, and starts a new period. */
    public LatencyMetrics getLatencyMetricsAndStartNewPeriod() {
        synchronized (monitor) {
            pushEndOfPeriodToNow();
            LatencyMetrics metrics = makeLatencyMetrics();
            resetForNewPeriod();
            return metrics;
        }
    }

    private static class ActiveIntervalInfo {
        private final long startNanos;
        public ActiveIntervalInfo(long startOfIntervalNanos) { this.startNanos = startOfIntervalNanos; }
        public long startOfIntervalNanos() { return startNanos; }
    }

    private void resetForNewPeriod() {
        startOfPeriodNanos = endOfPeriodNanos;
        cumulativeLoadNanos = 0.0;
        cumulativeLatency = Duration.ZERO;
        maxLatency = Duration.ZERO;
        numIntervalsStarted = 0;
        numIntervalsEnded = 0;
        maxLoad = activeIntervals.size();
    }

    private void pushEndOfPeriodToNow() {
        long currentNanos = nanoTimeSupplier.getAsLong();
        cumulativeLoadNanos += activeIntervals.size() * (currentNanos - endOfPeriodNanos);
        endOfPeriodNanos = currentNanos;
    }

    private void endInterval(ActiveIntervalInfo activeInterval) {
        boolean wasRemoved;
        synchronized (monitor) {
            pushEndOfPeriodToNow();
            wasRemoved = activeIntervals.remove(activeInterval);
            Duration latency = Duration.ofNanos(endOfPeriodNanos - activeInterval.startOfIntervalNanos());
            cumulativeLatency = cumulativeLatency.plus(latency);
            if (latency.compareTo(maxLatency) > 0) {
                maxLatency = latency;
            }
            ++numIntervalsEnded;
        }

        if (!wasRemoved) {
            // Exception made to dump stack trace.
            logger.log(Level.WARNING, "Interval of latency stats was closed twice", new IllegalStateException());
        }
    }

    /** Returns the metrics for the startOfPeriodNanos and endOfPeriodNanos period. */
    private LatencyMetrics makeLatencyMetrics() {
        Duration latency = numIntervalsEnded <= 0 ?
                Duration.ZERO :
                Duration.ofNanos(round(cumulativeLatency.toNanos() / (double) numIntervalsEnded));

        Optional<Duration> maxLatencyFromActiveIntervals = activeIntervals.stream()
                .map(ActiveIntervalInfo::startOfIntervalNanos)
                .min(Comparator.comparing(value -> value))
                .map(startOfIntervalNanos -> Duration.ofNanos(endOfPeriodNanos - startOfIntervalNanos));
        Duration maxActiveLatency = maxLatencyFromActiveIntervals
                .filter(latencyCandidate -> latencyCandidate.compareTo(maxLatency) > 0)
                .orElse(maxLatency);

        final double startHz, endHz, load;
        long periodNanos = endOfPeriodNanos - startOfPeriodNanos;
        if (periodNanos > 0) {
            double periodSeconds = periodNanos / 1_000_000_000.0;
            startHz = numIntervalsStarted / periodSeconds;
            endHz = numIntervalsEnded / periodSeconds;
            load = cumulativeLoadNanos / periodNanos;
        } else {
            startHz = endHz = 0.0;
            load = activeIntervals.size();
        }

        return new LatencyMetrics(latency,
                                  maxLatency,
                                  maxActiveLatency,
                                  startHz,
                                  endHz,
                                  load,
                                  maxLoad,
                                  activeIntervals.size());
    }
}
