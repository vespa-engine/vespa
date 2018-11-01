// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator;

import com.yahoo.time.TimeBudget;
import com.yahoo.vespa.orchestrator.controller.ClusterControllerClientTimeouts;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Context for an operation (or suboperation) of the Orchestrator that needs to pass through to the backend,
 * e.g. timeout management and probing.
 *
 * @author hakonhall
 */
public class OrchestratorContext {
    private static final Duration DEFAULT_TIMEOUT_FOR_SINGLE_OP = Duration.ofSeconds(10);
    private static final Duration DEFAULT_TIMEOUT_FOR_BATCH_OP = Duration.ofSeconds(60);

    private final Clock clock;
    private final TimeBudget timeBudget;
    private boolean probe;

    public static OrchestratorContext createContextForMultiAppOp(Clock clock) {
        return new OrchestratorContext(clock, TimeBudget.fromNow(clock, DEFAULT_TIMEOUT_FOR_BATCH_OP), true);
    }

    public static OrchestratorContext createContextForSingleAppOp(Clock clock) {
        return new OrchestratorContext(clock, TimeBudget.fromNow(clock, DEFAULT_TIMEOUT_FOR_SINGLE_OP), true);
    }

    private OrchestratorContext(Clock clock, TimeBudget timeBudget, boolean probe) {
        this.clock = clock;
        this.timeBudget = timeBudget;
        this.probe = probe;
    }

    public Duration getTimeLeft() {
        return timeBudget.timeLeftOrThrow().get();
    }

    public ClusterControllerClientTimeouts getClusterControllerTimeouts(String clusterName) {
        return new ClusterControllerClientTimeouts(clusterName, timeBudget.timeLeftAsTimeBudget());
    }


    /** Mark this operation as a non-committal probe. */
    public OrchestratorContext markAsProbe() {
        this.probe = true;
        return this;
    }

    /** Whether the operation is a no-op probe to test whether it would have succeeded, if it had been committal. */
    public boolean isProbe() {
        return probe;
    }

    /** Create an OrchestratorContext to use within an application lock. */
    public OrchestratorContext createSubcontextForApplication() {
        Instant now = clock.instant();
        Instant deadline = timeBudget.deadline().get();
        Instant maxDeadline = now.plus(DEFAULT_TIMEOUT_FOR_SINGLE_OP);
        if (maxDeadline.compareTo(deadline) < 0) {
            deadline = maxDeadline;
        }

        return new OrchestratorContext(
                clock,
                TimeBudget.from(clock, now, Optional.of(Duration.between(now, deadline))),
                probe);
    }
}
