// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator;

import com.yahoo.time.TimeBudget;
import com.yahoo.vespa.applicationmodel.ApplicationInstanceReference;
import com.yahoo.vespa.orchestrator.controller.ClusterControllerClientTimeouts;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Context for an operation (or suboperation) of the Orchestrator that needs to pass through to the backend,
 * e.g. timeout management and probing.
 *
 * @author hakonhall
 */
public class OrchestratorContext implements AutoCloseable {
    private static final Logger logger = Logger.getLogger(OrchestratorContext.class.getName());
    private static final Duration DEFAULT_TIMEOUT_FOR_SINGLE_OP = Duration.ofSeconds(10);
    private static final Duration DEFAULT_TIMEOUT_FOR_BATCH_OP = Duration.ofSeconds(60);
    private static final Duration DEFAULT_TIMEOUT_FOR_ADMIN_OP = Duration.ofMinutes(5);
    private static final Duration TIMEOUT_OVERHEAD = Duration.ofMillis(500);

    private final Optional<OrchestratorContext> parent;
    private final Clock clock;
    private final TimeBudget timeBudget;
    private final boolean probe;
    private final boolean largeLocks;

    // The key set is the set of applications locked by this context tree: Only the
    // root context has a non-empty set. The value is an unlock callback to be called
    // when the root context is closed.
    private final HashMap<ApplicationInstanceReference, Runnable> locks = new HashMap<>();

    /** Create an OrchestratorContext for operations on multiple applications. */
    public static OrchestratorContext createContextForMultiAppOp(Clock clock) {
        return new OrchestratorContext(null, clock, TimeBudget.fromNow(clock, DEFAULT_TIMEOUT_FOR_BATCH_OP),
                false, // probe
                true); // large locks
    }

    /** Create an OrchestratorContext for an operation on a single application. */
    public static OrchestratorContext createContextForSingleAppOp(Clock clock) {
        return new OrchestratorContext(null, clock, TimeBudget.fromNow(clock, DEFAULT_TIMEOUT_FOR_SINGLE_OP),
                                       false, false);
    }

    public static OrchestratorContext createContextForAdminOp(Clock clock) {
        return new OrchestratorContext(null, clock, TimeBudget.fromNow(clock, DEFAULT_TIMEOUT_FOR_ADMIN_OP),
                false, false);
    }

    public static OrchestratorContext createContextForBatchProbe(Clock clock) {
        return new OrchestratorContext(null, clock, TimeBudget.fromNow(clock, DEFAULT_TIMEOUT_FOR_BATCH_OP),
                                       true, false);
    }

    private OrchestratorContext(OrchestratorContext parentOrNull,
                                Clock clock,
                                TimeBudget timeBudget,
                                boolean probe,
                                boolean largeLocks) {
        this.parent = Optional.ofNullable(parentOrNull);
        this.clock = clock;
        this.timeBudget = timeBudget;
        this.probe = probe;
        this.largeLocks = largeLocks;
    }

    public Duration getTimeLeft() {
        return timeBudget.timeLeftOrThrow().get();
    }

    public ClusterControllerClientTimeouts getClusterControllerTimeouts() {
        return new ClusterControllerClientTimeouts(timeBudget.timeLeftAsTimeBudget());
    }

    /** Whether the operation is a no-op probe to test whether it would have succeeded, if it had been committal. */
    public boolean isProbe() {
        return probe;
    }

    /** Whether application locks acquired during probing of a batch suspend should be closed after the non-probe is done. */
    public boolean largeLocks() { return largeLocks; }

    /**
     * Returns true if 1. large locks is enabled, and 2.
     * {@link #registerLockAcquisition(ApplicationInstanceReference, Runnable) registerLockAcquisition}
     * has been invoked on any context below the root context that returned true.
     */
    public boolean hasLock(ApplicationInstanceReference application) {
        return parent.map(p -> p.hasLock(application)).orElseGet(() -> locks.containsKey(application));
    }

    /**
     * Returns true if large locks is enabled in the root context, and in case the unlock callback
     * will be invoked when the root context is closed.
     */
    public boolean registerLockAcquisition(ApplicationInstanceReference application, Runnable unlock) {
        if (parent.isPresent()) {
            return parent.get().registerLockAcquisition(application, unlock);
        }

        if (!largeLocks) {
            return false;
        }

        if (locks.containsKey(application)) {
            unlock.run();
            throw new IllegalStateException("Application " + application + " was already associated with a lock");
        }

        locks.put(application, unlock);

        return true;
    }

    /** Create OrchestratorContext to use within an application lock. */
    public OrchestratorContext createSubcontextWithinLock() {
        // Move deadline towards past by a fixed amount to ensure there's time to process exceptions and
        // access ZooKeeper before the lock times out.
        TimeBudget subTimeBudget = timeBudget.withDeadline(timeBudget.deadline().get().minus(TIMEOUT_OVERHEAD));
        return new OrchestratorContext(this, clock, subTimeBudget, probe, largeLocks);
    }

    /** Create an OrchestratorContext for an operation on a single application, but limited to current timeout. */
    public OrchestratorContext createSubcontextForSingleAppOp(boolean probe) {
        Instant now = clock.instant();
        Instant deadline = timeBudget.deadline().get();
        Instant maxDeadline = now.plus(DEFAULT_TIMEOUT_FOR_SINGLE_OP);
        if (maxDeadline.compareTo(deadline) < 0) {
            deadline = maxDeadline;
        }

        TimeBudget timeBudget = TimeBudget.from(clock, now, Optional.of(Duration.between(now, deadline)));
        return new OrchestratorContext(this, clock, timeBudget, probe, largeLocks);
    }

    @Override
    public void close() {
        locks.forEach((application, unlock) -> {
            try {
                unlock.run();
            } catch (RuntimeException e) {
                logger.log(Level.SEVERE, "Failed run on close : " + e.getMessage());
            }
        });
    }
}
