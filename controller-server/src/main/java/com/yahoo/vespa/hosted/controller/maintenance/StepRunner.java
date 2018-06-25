package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;

import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

import static com.yahoo.vespa.hosted.controller.deployment.Step.Status.aborted;
import static com.yahoo.vespa.hosted.controller.deployment.Step.Status.failed;
import static com.yahoo.vespa.hosted.controller.deployment.Step.Status.succeeded;
import static com.yahoo.vespa.hosted.controller.deployment.Step.Status.unfinished;

/**
 * Advances a given job run by running the appropriate {@link Step}s, based on their current status.
 *
 * When an attempt is made to advance a given job, a lock for that job (application and type) is
 * taken, and released again only when the attempt finishes. Multiple other attempts may be made in
 * the meantime, but they should give up unless the lock is promptly acquired.
 *
 * @author jonmv
 */
public class JobRunner {

    /**
     * Attempts to run the given step, and returns the new status.
     *
     * If the step fails,
     */
    RunStatus run(Step step, RunStatus run) {
        switch (step) {
            default: throw new AssertionError();
        }
    }

    private Step.Status deployInitialReal(ApplicationId id, JobType type) {
        throw new AssertionError();
    }

    /**
     * Attempts to advance the given job run by running the first eligible step, and returns the new status.
     *
     * Only the first unfinished step is attempted, to split the jobs into the smallest possible chunks, in case
     * of sudden shutdown, etc..
     */
    public RunStatus advance(RunStatus run, Instant now) {
        // If the run has failed, run any remaining alwaysRun steps, and return.
        if (run.status().values().contains(failed))
            return JobProfile.of(run.id().type()).alwaysRun().stream()
                             .filter(step -> run.status().get(step) == unfinished)
                             .findFirst()
                             .map(step -> run(step, run))
                             .orElse(run.with(now));

        // Otherwise, try to run the first unfinished step.
        return run.status().entrySet().stream()
                  .filter(entry ->    entry.getValue() == unfinished
                                   && entry.getKey().prerequisites().stream()
                                           .filter(run.status().keySet()::contains)
                                           .map(run.status()::get)
                                           .allMatch(succeeded::equals))
                  .findFirst()
                  .map(entry -> run(entry.getKey(), run))
                  .orElse(run.with(now));
    }

    RunStatus forceEnd(RunStatus run) {
        // Run each pending alwaysRun step.
        throw new AssertionError();
    }

}
