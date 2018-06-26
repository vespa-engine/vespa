package com.yahoo.vespa.hosted.controller.deployment;

import com.google.common.collect.ImmutableMap;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.curator.Lock;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.LogStore;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RunId;
import com.yahoo.vespa.hosted.controller.application.ApplicationVersion;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import static com.google.common.collect.ImmutableList.copyOf;

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
    private final CuratorDb curator;
    private final LogStore logs;

    public JobController(Controller controller, LogStore logStore) {
        this.controller = controller;
        this.curator = controller.curator();
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
        return copyOf(controller.applications().asList().stream()
                                .filter(application -> application.deploymentJobs().builtInternally())
                                .map(Application::id)
                                .iterator());
    }

    /** Returns all job types which have been run for the given application. */
    public List<JobType> jobs(ApplicationId id) {
        return copyOf(Stream.of(JobType.values())
                            .filter(type -> last(id, type).isPresent())
                            .iterator());
    }

    public Optional<RunStatus> last(ApplicationId id, JobType type) {
        return curator.readLastRun(id, type);
    }

    /** Returns a list of meta information about all known runs of the given job type for the given application. */
    public Map<RunId, RunStatus> runs(ApplicationId id, JobType type) {
        ImmutableMap.Builder<RunId, RunStatus> runs = ImmutableMap.builder();
        runs.putAll(curator.readHistoricRuns(id, type));
        last(id, type).ifPresent(run -> runs.put(run.id(), run));
        return runs.build();
    }

    public List<RunStatus> active() {
        return copyOf(applications().stream()
                                    .flatMap(id -> Stream.of(JobType.values())
                                                         .map(type -> last(id, type))
                                                         .filter(Optional::isPresent).map(Optional::get)
                                                         .filter(run -> ! run.end().isPresent()))
                                    .iterator());
    }

    public Optional<RunStatus> active(RunId id) {
        return last(id.application(), id.type())
                .filter(run -> ! run.end().isPresent())
                .filter(run -> run.id().equals(id));
    }

    public void update(RunId id, Step.Status status, LockedStep step) {
        modify(id, run -> run.with(status, step));
    }

    public void finish(RunId id) {
        modify(id, run -> { // Store the modified run after it has been written to the collection, in case the latter fails.
            RunStatus endedRun = run.finish(controller.clock().instant());
            modify(id.application(), id.type(), runs -> runs.put(run.id(), endedRun));
            return endedRun;
        });
    }

    /** Returns the details for the given job. */
    public RunDetails details(RunId id) {
        return new RunDetails(logs.getPrepareResponse(id), logs.getConvergenceLog(id), logs.getTestLog(id));
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
    public void run(ApplicationId id, JobType type) {
        modify(id, type, runs -> {
            Optional<RunStatus> last = last(id, type);
            if (last.flatMap(run -> active(run.id())).isPresent())
                throw new IllegalStateException("Can not start " + type + " for " + id + "; it is already running!");

            RunId newId = new RunId(id, type, last.map(run -> run.id().number()).orElse(0L) + 1);
            curator.writeLastRun(RunStatus.initial(newId, controller.clock().instant()));
        });
    }

    /** Unregisters the given application, and deletes all associated data. */
    public void unregister(ApplicationId id) {
        controller.applications().lockIfPresent(id, application -> {
            controller.applications().store(application.withBuiltInternally(false));
        });
        jobs(id).forEach(type -> modify(id, type, __ -> curator.deleteRuns(id, type)));
    }

    /** Aborts the given job. */
    public void abort(RunId id) {

    }


    private ApplicationVersion nextVersion(ApplicationId id) {
        throw new AssertionError();
    }

    private void notifyOfNewSubmission(ApplicationId id) {
        ;
    }

    private void modify(ApplicationId id, JobType type, Consumer<Map<RunId, RunStatus>> modifications) {
        try (Lock __ = curator.lock(id, type)) {
            Map<RunId, RunStatus> runs = curator.readHistoricRuns(id, type);
            modifications.accept(runs);
            curator.writeHistoricRuns(id, type, runs.values());
        }
    }

    private void modify(RunId id, UnaryOperator<RunStatus> modifications) {
        try (Lock __ = curator.lock(id.application(), id.type())) {
            RunStatus run = active(id).orElseThrow(() -> new IllegalArgumentException(id + " is not an active run!"));
            run = modifications.apply(run);
            curator.writeLastRun(run);
        }
    }

    public void locked(RunId id, Step step, Consumer<LockedStep> action) throws TimeoutException {
        try (Lock lock = curator.lock(id.application(), id.type(), step)) {
            for (Step prerequisite : step.prerequisites()) // Check that no prerequisite is still running.
                try (Lock __ = curator.lock(id.application(), id.type(), prerequisite)) { ; }

            action.accept(new LockedStep(lock, step));
        }
    }

}
