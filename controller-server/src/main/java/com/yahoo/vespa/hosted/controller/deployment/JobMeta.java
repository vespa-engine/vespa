package com.yahoo.vespa.hosted.controller.deployment;

import java.time.Instant;

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

}
