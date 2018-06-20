package com.yahoo.vespa.hosted.controller.deployment;

import java.time.Instant;
import java.util.Optional;

/**
 * Contains state information for a deployment job run by an {@link InternalBuildService}.
 *
 * @author jonmv
 */
public class JobMeta {

    private final JobId id;
    private final JobState state;
    private final JobOutcome outcome;
    private final Instant start;
    private final Instant end;

    public JobMeta(JobId id, JobState state, JobOutcome outcome, Instant start, Instant end) {
        this.id = id;
        this.state = state;
        this.outcome = outcome;
        this.start = start;
        this.end = end;
    }

    public JobId id() {
        return id;
    }

    public JobState state() {
        return state;
    }

    public JobOutcome outcome() {
        return outcome;
    }

    public Instant start() {
        return start;
    }

    public Optional<Instant> end() {
        return Optional.ofNullable(end);
    }
}
