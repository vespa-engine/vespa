// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.concurrent.maintenance;

import com.yahoo.concurrent.UncheckedTimeoutException;
import com.yahoo.net.HostName;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The base class for maintainers. A maintainer is some job which runs at a fixed rate to perform maintenance tasks.
 *
 * @author bratseth
 * @author mpolden
 * @author jonmv
 */
public abstract class Maintainer implements Runnable {

    protected final Logger log = Logger.getLogger(this.getClass().getName());

    private final String name;
    private final JobControl jobControl;
    private final JobMetrics jobMetrics;
    private final Duration interval;
    private final ScheduledExecutorService service;
    private final AtomicBoolean shutDown = new AtomicBoolean();
    private final boolean ignoreCollision;
    private final Clock clock;
    private final double successFactorBaseline;

    public Maintainer(String name, Duration interval, Clock clock, JobControl jobControl,
                      JobMetrics jobMetrics, List<String> clusterHostnames, boolean ignoreCollision, double successFactorBaseline) {
        this.name = name;
        this.interval = requireInterval(interval);
        this.jobControl = Objects.requireNonNull(jobControl);
        this.jobMetrics = Objects.requireNonNull(jobMetrics);
        this.ignoreCollision = ignoreCollision;
        this.clock = clock;
        this.successFactorBaseline = successFactorBaseline;
        var startedAt = clock.instant();
        Objects.requireNonNull(clusterHostnames);
        Duration initialDelay = staggeredDelay(interval, startedAt, HostName.getLocalhost(), clusterHostnames)
                                .plus(Duration.ofSeconds(30)); // Let the system stabilize before maintenance
        service = new ScheduledThreadPoolExecutor(1, r -> new Thread(r, name() + "-worker"));
        service.scheduleAtFixedRate(this, initialDelay.toMillis(), interval.toMillis(), TimeUnit.MILLISECONDS);
        jobControl.started(name(), this);
    }

    public Maintainer(String name, Duration interval, Clock clock, JobControl jobControl,
                      JobMetrics jobMetrics, List<String> clusterHostnames, boolean ignoreCollision) {
        this(name, interval, clock, jobControl, jobMetrics, clusterHostnames, ignoreCollision, 1.0);
    }
    @Override
    public void run() {
        lockAndMaintain(false);
    }

    /** Starts shutdown of this, typically by shutting down executors. {@link #awaitShutdown()} waits for shutdown to complete. */
    public void shutdown() {
        if ( ! shutDown.getAndSet(true))
            service.shutdown();
    }

    /** Waits for shutdown to complete, calling {@link #shutdown} if this hasn't been done already. */
    public void awaitShutdown() {
        shutdown();
        var timeout = Duration.ofSeconds(30);
        try {
            if (!service.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                log.log(Level.WARNING, "Maintainer " + name() + " failed to shutdown " +
                                       "within " + timeout);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /** Returns whether this is being shut down */
    public final boolean shuttingDown() {
        return shutDown.get();
    }

    @Override
    public final String toString() { return name(); }

    /**
     * Called once each time this maintenance job should run.
     *
     * @return the degree to which the run successFactor deviated from the successFactorBaseline
     *             - a number between -1 (no success), to 0 (complete success) measured against the
     *             successFactorBaseline, or higher if the success factor is higher than the successFactorBaseline.
     *         The default successFactorBaseline is 1.0.
     *         If a maintainer is expected to fail sometimes, the successFactorBaseline should be set to a lower value.
     *
     *         Note that this indicates whether something is wrong, so e.g. if the call did nothing because it should do
     *         nothing, 0.0 should be returned.
     */
    protected abstract double maintain();

    /** Convenience methods to convert attempts and failures into a success factor deviation from the baseline, and return   */
    protected final double asSuccessFactorDeviation(int attempts, int failures) {
        double factor = attempts == 0 ? 1.0 : 1 - (double)failures / attempts;
        return new BigDecimal(factor - successFactorBaseline).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    /** Returns the interval at which this job is set to run */
    protected Duration interval() { return interval; }

    /** Run this while holding the job lock */
    public final void lockAndMaintain(boolean force) {
        if (!force && !jobControl.isActive(name())) return;
        log.log(Level.FINE, () -> "Running " + this.getClass().getSimpleName());

        double successFactorDeviation = -1;
        long startTime = clock.millis();
        try (var lock = jobControl.lockJob(name())) {
            successFactorDeviation = maintain();
        }
        catch (UncheckedTimeoutException e) {
            if (ignoreCollision)
                successFactorDeviation = 0;
            else
                log.log(Level.WARNING, this + " collided with another run. Will retry in " + interval);
        }
        catch (Throwable e) {
            log.log(Level.WARNING, this + " failed. Will retry in " + interval, e);
        }
        finally {
            long endTime = clock.millis();
            jobMetrics.completed(name(), successFactorDeviation, endTime - startTime);
        }
        log.log(Level.FINE, () -> "Finished " + this.getClass().getSimpleName());
    }

    /** Returns the simple name of this job */
    public final String name() {
        return name == null ? this.getClass().getSimpleName() : name;
    }

    /** Returns the initial delay of this calculated from cluster index of given hostname */
    static Duration staggeredDelay(Duration interval, Instant now, String hostname, List<String> clusterHostnames) {
        Objects.requireNonNull(clusterHostnames);
        if ( ! clusterHostnames.contains(hostname))
            return interval;

        long offset = clusterHostnames.indexOf(hostname) * interval.toMillis() / clusterHostnames.size();
        return Duration.ofMillis(Math.floorMod(offset - now.toEpochMilli(), interval.toMillis()));
    }

    private static Duration requireInterval(Duration interval) {
        Objects.requireNonNull(interval);
        if (interval.isNegative() || interval.isZero())
            throw new IllegalArgumentException("Interval must be positive, but was " + interval);
        return interval;
    }

}
