// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator;

import com.yahoo.log.LogLevel;
import com.yahoo.time.TimeBudget;
import com.yahoo.vespa.orchestrator.controller.ClusterControllerClientTimeouts;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
    private static final Duration TIMEOUT_OVERHEAD = Duration.ofMillis(500);

    private final Clock clock;
    private final boolean partOfMultiAppOp;
    private final TimeBudget timeBudget;
    private final boolean probe;
    private final boolean largeLocks;
    private final List<Runnable> onClose = new ArrayList<>();

    /** Create an OrchestratorContext for operations on multiple applications. */
    public static OrchestratorContext createContextForMultiAppOp(Clock clock, boolean largeLocks) {
        return new OrchestratorContext(clock, true, TimeBudget.fromNow(clock, DEFAULT_TIMEOUT_FOR_BATCH_OP), false, largeLocks);
    }

    /** Create an OrchestratorContext for an operation on a single application. */
    public static OrchestratorContext createContextForSingleAppOp(Clock clock) {
        return new OrchestratorContext(clock, false, TimeBudget.fromNow(clock, DEFAULT_TIMEOUT_FOR_SINGLE_OP), false, false);
    }

    private OrchestratorContext(Clock clock, boolean partOfMultiAppOp, TimeBudget timeBudget, boolean probe, boolean largeLocks) {
        this.clock = clock;
        this.partOfMultiAppOp = partOfMultiAppOp;
        this.timeBudget = timeBudget;
        this.probe = probe;
        this.largeLocks = largeLocks;
    }

    public boolean partOfMultiAppOp() { return partOfMultiAppOp; }

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

    /** Create OrchestratorContext to use within an application lock. */
    public OrchestratorContext createSubcontextWithinLock() {
        // Move deadline towards past by a fixed amount to ensure there's time to process exceptions and
        // access ZooKeeper before the lock times out.
        TimeBudget subTimeBudget = timeBudget.withDeadline(timeBudget.deadline().get().minus(TIMEOUT_OVERHEAD));
        return new OrchestratorContext(clock, partOfMultiAppOp, subTimeBudget, probe, false);
    }

    /** Create an OrchestratorContext for an operation on a single application, but limited to current timeout. */
    public OrchestratorContext createSubcontextForSingleAppOp(boolean probe) {
        Instant now = clock.instant();
        Instant deadline = timeBudget.deadline().get();
        Instant maxDeadline = now.plus(DEFAULT_TIMEOUT_FOR_SINGLE_OP);
        if (maxDeadline.compareTo(deadline) < 0) {
            deadline = maxDeadline;
        }

        return new OrchestratorContext(
                clock,
                partOfMultiAppOp,
                TimeBudget.from(clock, now, Optional.of(Duration.between(now, deadline))),
                probe,
                false);
    }

    public void runOnClose(Runnable runnable) { onClose.add(runnable); }

    @Override
    public void close() {
        int i = onClose.size();
        while (i --> 0) {
            try {
                onClose.get(i).run();
            } catch (RuntimeException e) {
                logger.log(LogLevel.ERROR, "Failed run on close : " + e.getMessage());
            }
        }
    }
}
