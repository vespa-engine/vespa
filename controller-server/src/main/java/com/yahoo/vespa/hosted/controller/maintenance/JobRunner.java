package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.log.LogLevel;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.deployment.JobController;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RunId;
import com.yahoo.vespa.hosted.controller.deployment.Run;
import com.yahoo.vespa.hosted.controller.deployment.RunStatus;
import com.yahoo.vespa.hosted.controller.deployment.Step;
import com.yahoo.vespa.hosted.controller.deployment.StepRunner;
import org.jetbrains.annotations.TestOnly;

import java.time.Duration;
import java.util.List;
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

    public JobRunner(Controller controller, Duration duration, JobControl jobControl, StepRunner runner) {
        this(controller, duration, jobControl, Executors.newFixedThreadPool(32), runner);
    }

    @TestOnly
    public JobRunner(Controller controller, Duration duration, JobControl jobControl, ExecutorService executors, StepRunner runner) {
        super(controller, duration, jobControl);
        this.jobs = controller.jobController();
        this.executors = executors;
        this.runner = runner;
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

    /** Advances each of the ready steps for the given run, or marks it as finished, and stashes it. */
    private void advance(Run run) {
        List<Step> steps = run.readySteps();
        steps.forEach(step -> executors.execute(() -> advance(run.id(), step)));
        if (steps.isEmpty())
            jobs.finish(run.id());
        else if (   run.status() != RunStatus.aborted
                 && run.start().isBefore(controller().clock().instant().minus(jobTimeout)))
            jobs.abort(run.id());
    }

    /** Attempts to advance the status of the given step, for the given run. */
    private void advance(RunId id, Step step) {
        try {
            AtomicBoolean changed = new AtomicBoolean(false);
            jobs.locked(id.application(), id.type(), step, lockedStep -> {
                jobs.active(id).ifPresent(run -> { // The run may have become inactive, so we bail out.
                    if ( ! run.readySteps().contains(step))
                        return; // Someone may have updated the run status, making this step obsolete, so we bail out.

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
