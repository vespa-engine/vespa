// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator;

import com.yahoo.jdisc.TimeBudget;
import com.yahoo.jdisc.Timer;

import java.time.Duration;
import java.util.Optional;

/**
 * Context for the Orchestrator, e.g. timeout management.
 *
 * @author hakon
 */
public class OrchestratorContext {
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration POST_OPERATION_HEADROOM = Duration.ofMillis(100);

    private TimeBudget timeBudget;

    public OrchestratorContext(Timer timer) {
        this.timeBudget = TimeBudget.fromNow(timer, DEFAULT_TIMEOUT);
    }

    /** Get the original timeout in seconds. */
    public long getOriginalTimeoutInSeconds() {
        return timeBudget.originalTimeout().getSeconds();
    }

    /**
     * Get number of seconds until the deadline, or empty if there's no deadline.
     *
     * <p>The returned timeout is slightly shorter than the actual timeout to ensure there's
     * enough time to wrap up and return from the Orchestrator between when the operation
     * times out and the actual timeout.
     */
    public Optional<Float> getSuboperationTimeoutInSeconds() {
        return getSuboperationTimeoutInSeconds(POST_OPERATION_HEADROOM);
    }

    private Optional<Float> getSuboperationTimeoutInSeconds(Duration headroom) {
        return Optional.of((float) (timeBudget.timeBeforeDeadline(headroom).toMillis() / 1000.0));
    }
}
