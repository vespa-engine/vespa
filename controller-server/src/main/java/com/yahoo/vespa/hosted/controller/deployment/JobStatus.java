// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RunId;

import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;

/**
 * Aggregates information about all known runs of a given job to provide the high level status.
 *
 * @author jonmv
 */
public class JobStatus {

    private final JobId id;
    private final NavigableMap<RunId, Run> runs;
    private final Optional<Run> lastTriggered;
    private final Optional<Run> lastCompleted;
    private final Optional<Run> lastSuccess;
    private final Optional<Run> firstFailing;

    public JobStatus(JobId id, NavigableMap<RunId, Run> runs) {
        this.id = Objects.requireNonNull(id);
        this.runs = Objects.requireNonNull(runs);
        this.lastTriggered = runs.descendingMap().values().stream().findFirst();
        this.lastCompleted = lastCompleted(runs);
        this.lastSuccess = lastSuccess(runs);
        this.firstFailing = firstFailing(runs);
    }

    public JobId id() {
        return id;
    }

    public NavigableMap<RunId, Run> runs() {
        return runs;
    }

    public Optional<Run> lastTriggered() {
        return lastTriggered;
    }

    public Optional<Run> lastCompleted() {
        return lastCompleted;
    }

    public Optional<Run> lastSuccess() {
        return lastSuccess;
    }

    public Optional<Run> firstFailing() {
        return firstFailing;
    }

    public Optional<RunStatus> lastStatus() {
        return lastCompleted().map(Run::status);
    }

    public boolean isSuccess() {
        return lastCompleted.map(last -> ! last.hasFailed()).orElse(false);
    }

    public boolean isRunning() {
        return lastTriggered.isPresent() && ! lastTriggered.get().hasEnded();
    }

    public boolean isNodeAllocationFailure() {
        return lastStatus().isPresent() && lastStatus().get() == RunStatus.nodeAllocationFailure;
    }

    @Override
    public String toString() {
        return "JobStatus{" +
               "id=" + id +
               ", lastTriggered=" + lastTriggered +
               ", lastCompleted=" + lastCompleted +
               ", lastSuccess=" + lastSuccess +
               ", firstFailing=" + firstFailing +
               '}';
    }

    static Optional<Run> lastCompleted(NavigableMap<RunId, Run> runs) {
        return runs.descendingMap().values().stream()
                   .filter(run -> run.hasEnded())
                   .findFirst();
    }

    static Optional<Run> lastSuccess(NavigableMap<RunId, Run> runs) {
        return runs.descendingMap().values().stream()
                   .filter(Run::hasSucceeded)
                   .findFirst();
    }

    static Optional<Run> firstFailing(NavigableMap<RunId, Run> runs) {
        Run failed = null;
        for (Run run : runs.descendingMap().values()) {
            if ( ! run.hasEnded()) continue;
            if ( ! run.hasFailed()) break;
            failed = run;
        }
        return Optional.ofNullable(failed);
    }

}
