// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.log.LogLevel;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RunId;
import com.yahoo.vespa.hosted.controller.deployment.InternalStepRunner;
import com.yahoo.vespa.hosted.controller.deployment.JobController;
import com.yahoo.vespa.hosted.controller.deployment.Run;
import com.yahoo.vespa.hosted.controller.deployment.Step;
import com.yahoo.vespa.hosted.controller.deployment.StepInfo;
import com.yahoo.vespa.hosted.controller.deployment.StepRunner;
import org.jetbrains.annotations.TestOnly;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * Advances the set of {@link Run}s for a {@link JobController}.
 *
 * @author jonmv
 */
public class JobRunner extends Maintainer {

    static final Duration jobTimeout = Duration.ofDays(1);
    private static final Logger log = Logger.getLogger(JobRunner.class.getName());

    private final JobController jobs;
    private final ExecutorService executors;
    private final StepRunner runner;
    private final Clock clock;

    public JobRunner(Controller controller, Duration duration, JobControl jobControl, Clock clock) {
        this(controller, duration, jobControl, Executors.newFixedThreadPool(32), new InternalStepRunner(controller), clock);
    }

    @TestOnly
    public JobRunner(Controller controller, Duration duration, JobControl jobControl, ExecutorService executors,
                     StepRunner runner, Clock clock) {
        super(controller, duration, jobControl);
        this.jobs = controller.jobController();
        this.jobs.setRunner(this::advance);
        this.executors = executors;
        this.runner = runner;
        this.clock = clock;
    }

    @Override
    protected void maintain() {
        jobs.active().forEach(this::advance);
        jobs.collectGarbage();
    }

    @Override
    public void deconstruct() {
        super.deconstruct();
        executors.shutdown();
        try {
            executors.awaitTermination(50, TimeUnit.SECONDS);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Advances each of the ready steps for the given run, or marks it as finished, and stashes it. Public for testing. */
    public void advance(Run run) {
        if (   ! run.hasFailed()
            &&   run.start().isBefore(controller().clock().instant().minus(jobTimeout))) {
            jobs.abort(run.id());
            advance(jobs.run(run.id()).get());
        }
        else if (run.readySteps().isEmpty())
            executors.execute(() -> finish(run.id()));
        else
            run.readySteps().forEach(step -> executors.execute(() -> advance(run.id(), step)));
    }

    private void finish(RunId id) {
        try {
            jobs.finish(id);
            controller().jobController().run(id)
                        .ifPresent(run -> controller().applications().deploymentTrigger().notifyOfCompletion(id.application()));
        }
        catch (Exception e) {
            log.log(LogLevel.WARNING, "Exception finishing " + id, e);
        }
    }

    /** Attempts to advance the status of the given step, for the given run. */
    private void advance(RunId id, Step step) {
        try {
            AtomicBoolean changed = new AtomicBoolean(false);
            jobs.locked(id.application(), id.type(), step, lockedStep -> {
                jobs.locked(id, run -> run); // Memory visibility.
                jobs.active(id).ifPresent(run -> { // The run may have become inactive, so we bail out.
                    if ( ! run.readySteps().contains(step))
                        return; // Someone may have updated the run status, making this step obsolete, so we bail out.

                    StepInfo stepInfo = run.stepInfo(lockedStep.get()).orElseThrow();
                    if (stepInfo.startTime().isEmpty()) {
                        jobs.setStartTimestamp(run.id(), clock.instant(), lockedStep);
                    }

                    runner.run(lockedStep, run.id()).ifPresent(status -> {
                        jobs.update(run.id(), status, lockedStep);
                        changed.set(true);
                    });
                });
            });
            if (changed.get())
                jobs.active(id).ifPresent(this::advance);
        }
        catch (TimeoutException e) {
            // Something else is already advancing this step, or a prerequisite -- try again later!
        }
        catch (RuntimeException e) {
            log.log(LogLevel.WARNING, "Exception attempting to advance " + step + " of " + id, e);
        }
    }

}
