package com.yahoo.vespa.hosted.controller.deployment;

import com.google.common.collect.ImmutableList;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.curator.Lock;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.LogStore;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RunId;
import com.yahoo.vespa.hosted.controller.application.ApplicationVersion;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * A singleton owned by the controller, which contains the state and methods for controlling deployment jobs.
 *
 * Keys are the {@link ApplicationId} of the real application, for which the deployment job is run, and the
 * {@link JobType} of the real deployment to test.
 *
 * Although the deployment jobs are themselves applications, their IDs are not to be referenced.
 *
 * @author jonmv
 */
public class JobController {

    private final Controller controller;
    private final LogStore logs;

    public JobController(Controller controller, LogStore logStore) {
        this.controller = controller;
        this.logs = logStore;
    }

    /** Returns whether the given application has registered with this build service. */
    public boolean builds(ApplicationId id) {
        return controller.applications().get(id)
                         .map(application -> application.deploymentJobs().builtInternally())
                         .orElse(false);
    }

    /** Returns a list of all application which have registered. */
    public List<ApplicationId> applications() {
        return null;
    }

    /** Returns all job types which have been run for the given application. */
    public List<JobType> jobs(ApplicationId id) {
        return ImmutableList.copyOf(Stream.of(JobType.values())
                                          .filter(type -> ! runs(id, type).isEmpty())
                                          .iterator());
    }

    /** Returns a list of meta information about all known runs of the given job type for the given application. */
    public List<RunStatus> runs(ApplicationId id, JobType type) {
        ImmutableList.Builder<RunStatus> runs = ImmutableList.builder();
        runs.addAll(controller.curator().readHistoricRuns(id, type));
        activeRuns().stream()
                    .filter(run -> run.id().application().equals(id) && run.id().type() == type)
                    .forEach(runs::add);
        return runs.build();
    }

    List<RunStatus> activeRuns() {
        return controller.curator().readActiveRuns();
    }

    /** Returns the updated status of the given job, if it is active. */
    public Optional<RunStatus> currentStatus(RunId id) {
        try (Lock __ = controller.curator().lockActiveRuns()) {
            return activeRuns().stream() // TODO jvenstad: Change these to Map<RunId, RunStatus>.
                               .filter(run -> run.id().equals(id))
                               .findAny();
        }
    }

    public Optional<RunStatus> update(RunId id, Step.Status status, LockedStep step) {
        return currentStatus(id).map(run -> {
            run = run.with(status, step);
            controller.curator().writeActiveRun(run);
            return run;
        });
    }

    public void locked(RunId id, Step step, Consumer<LockedStep> action) {
        try (Lock lock = controller.curator().lock(id.application(), id.type(), step)) {
            for (Step prerequisite : step.prerequisites()) // Check that no prerequisite is still running.
                try (Lock __ = controller.curator().lock(id.application(), id.type(), prerequisite)) { ; }

            action.accept(new LockedStep(lock, step));
        }
        catch (TimeoutException e) {
            // Something else is already running that step, or a prerequisite -- try again later!
        }
    }

    public void finish(RunId id) {
        controller.applications().lockIfPresent(id.application(), __ -> {
            currentStatus(id).ifPresent(run -> {
                controller.curator().writeHistoricRun(run.with(controller.clock().instant()));
            });
        });
    }

    /** Returns the details for the given job. */
    public RunDetails details(RunId id) {
        try (Lock __ = controller.curator().lock(id.application(), id.type())) {
            return new RunDetails(logs.getPrepareResponse(id), logs.getConvergenceLog(id), logs.getTestLog(id));
        }
    }

    /** Registers the given application, such that it may have deployment jobs run here. */
    void register(ApplicationId id) {
        controller.applications().lockIfPresent(id, application ->
                controller.applications().store(application.withBuiltInternally(true)));
    }

    /** Accepts and stores a new appliaction package and test jar pair. */
    public void submit(ApplicationId id, byte[] applicationPackage, byte[] applicationTestJar) {
        controller.applications().lockOrThrow(id, application -> {
            ApplicationVersion version = nextVersion(id);

            // TODO smorgrav: Store the pair.

            notifyOfNewSubmission(id);
        });
    }

    /** Orders a run of the given type, and returns the id of the created job. */
    public RunId run(ApplicationId id, JobType type) {
        try (Lock __ = controller.curator().lock(id, type);
             Lock ___ = controller.curator().lockActiveRuns()) {
            List<RunStatus> runs = controller.curator().readHistoricRuns(id, type);
        }
        return null;
    }

    /** Unregisters the given application, and deletes all associated data. */
    public void unregister(ApplicationId id) {
        controller.applications().lockIfPresent(id, application -> {

            // TODO jvenstad: Clean out data for jobs.

            controller.applications().store(application.withBuiltInternally(false));
        });
    }

    /** Aborts the given job. */
    public void abort(RunId id) {
        ;
    }


    private void advanceJobs() {
        activeRuns().forEach(run -> {

        });
    }

    private ApplicationVersion nextVersion(ApplicationId id) {
        throw new AssertionError();
    }

    private void notifyOfNewSubmission(ApplicationId id) {
        ;
    }

}
