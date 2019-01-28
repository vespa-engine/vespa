// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.deployment;

import com.google.common.collect.ImmutableMap;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.curator.Lock;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.integration.LogEntry;
import com.yahoo.vespa.hosted.controller.api.integration.RunDataStore;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.NoInstanceException;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.ApplicationVersion;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RunId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.SourceRevision;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.TesterCloud;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.TesterId;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs;
import com.yahoo.vespa.hosted.controller.application.JobStatus;
import com.yahoo.vespa.hosted.controller.persistence.BufferedLogStore;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.logging.Level;
import java.util.stream.Stream;

import static com.google.common.collect.ImmutableList.copyOf;
import static com.yahoo.vespa.hosted.controller.deployment.Step.deactivateTester;
import static com.yahoo.vespa.hosted.controller.deployment.Step.endTests;
import static java.util.stream.Collectors.toList;

/**
 * A singleton owned by the controller, which contains the state and methods for controlling deployment jobs.
 *
 * Keys are the {@link ApplicationId} of the real application, for which the deployment job is run, the
 * {@link JobType} to run, and the strictly increasing run number of this combination.
 * The deployment jobs run tests using regular applications, but these tester application IDs are not to be used elsewhere.
 *
 * Jobs consist of sets of {@link Step}s, defined in {@link JobProfile}s.
 * Each run is represented by a {@link Run}, which holds the status of each step of the run, as well as
 * some other meta data.
 *
 * @author jonmv
 */
public class JobController {

    private static final int historyLength = 256;

    private final Controller controller;
    private final CuratorDb curator;
    private final BufferedLogStore logs;
    private final TesterCloud cloud;
    private final Badges badges;

    public JobController(Controller controller, RunDataStore runDataStore, TesterCloud testerCloud) {
        this.controller = controller;
        this.curator = controller.curator();
        this.logs = new BufferedLogStore(curator, runDataStore);
        this.cloud = testerCloud;
        this.badges = new Badges(controller.zoneRegistry().badgeUrl());
    }

    public TesterCloud cloud() { return cloud; }
    public int historyLength() { return historyLength; }

    /** Rewrite all job data with the newest format. */
    public void updateStorage() {
        for (ApplicationId id : applications())
            for (JobType type : jobs(id)) {
                locked(id, type, runs -> { // runs is not modified here, and is written as it was.
                    curator.readLastRun(id, type).ifPresent(curator::writeLastRun);
                });
            }
    }

    /** Returns all entries currently logged for the given run. */
    public Optional<RunLog> details(RunId id) {
        return details(id, -1);
    }

    /** Returns the logged entries for the given run, which are after the given id threshold. */
    public Optional<RunLog> details(RunId id, long after) {
        try (Lock __ = curator.lock(id.application(), id.type())) {
            Run run = runs(id.application(), id.type()).get(id);
            if (run == null)
                return Optional.empty();

            return active(id).isPresent()
                    ? Optional.of(logs.readActive(id.application(), id.type(), after))
                    : logs.readFinished(id, after);
        }
    }

    /** Stores the given log records for the given run and step. */
    public void log(RunId id, Step step, Level level, List<String> messages) {
        locked(id, __ -> {
            List<LogEntry> entries = messages.stream()
                                             .map(message -> new LogEntry(0, controller.clock().millis(), LogEntry.typeOf(level), message))
                                             .collect(toList());
            logs.append(id.application(), id.type(), step, entries);
            return __;
        });
    }

    /** Stores the given log record for the given run and step. */
    public void log(RunId id, Step step, Level level, String message) {
        log(id, step, level, Collections.singletonList(message));
    }

    /** Fetches any new test log entries, and records the id of the last of these, for continuation. */
    public void updateTestLog(RunId id) {
            locked(id, run -> {
                if ( ! run.readySteps().contains(endTests))
                    return run;

                Optional<URI> testerEndpoint = testerEndpoint(id);
                if ( ! testerEndpoint.isPresent())
                    return run;

                List<LogEntry> entries = cloud.getLog(testerEndpoint.get(), run.lastTestLogEntry());
                if (entries.isEmpty())
                    return run;

                logs.append(id.application(), id.type(), endTests, entries);
                return run.with(entries.stream().mapToLong(LogEntry::id).max().getAsLong());
            });
    }

    /** Returns a list of all application which have registered. */
    public List<ApplicationId> applications() {
        return copyOf(controller.applications().asList().stream()
                                .filter(application -> application.deploymentJobs().deployedInternally())
                                .map(Application::id)
                                .iterator());
    }

    /** Returns all job types which have been run for the given application. */
    public List<JobType> jobs(ApplicationId id) {
        return copyOf(Stream.of(JobType.values())
                            .filter(type -> last(id, type).isPresent())
                            .iterator());
    }

    /** Returns an immutable map of all known runs for the given application and job type. */
    public Map<RunId, Run> runs(ApplicationId id, JobType type) {
        SortedMap<RunId, Run> runs = curator.readHistoricRuns(id, type);
        last(id, type).ifPresent(run -> runs.putIfAbsent(run.id(), run));
        return ImmutableMap.copyOf(runs);
    }

    /** Returns the run with the given id, if it exists. */
    public Optional<Run> run(RunId id) {
        return runs(id.application(), id.type()).values().stream()
                                                .filter(run -> run.id().equals(id))
                                                .findAny();
    }

    /** Returns the last run of the given type, for the given application, if one has been run. */
    public Optional<Run> last(ApplicationId id, JobType type) {
        return curator.readLastRun(id, type);
    }

    /** Returns the run with the given id, provided it is still active. */
    public Optional<Run> active(RunId id) {
        return last(id.application(), id.type())
                .filter(run -> ! run.hasEnded())
                .filter(run -> run.id().equals(id));
    }

    /** Returns a list of all active runs. */
    public List<Run> active() {
        return copyOf(applications().stream()
                                    .flatMap(id -> Stream.of(JobType.values())
                                                         .map(type -> last(id, type))
                                                         .flatMap(Optional::stream)
                                                         .filter(run -> ! run.hasEnded()))
                                    .iterator());
    }

    /** Changes the status of the given step, for the given run, provided it is still active. */
    public void update(RunId id, RunStatus status, LockedStep step) {
        locked(id, run -> run.with(status, step));
    }

    /** Changes the status of the given run to inactive, and stores it as a historic run. */
    public void finish(RunId id) {
        locked(id, run -> { // Store the modified run after it has been written to history, in case the latter fails.
            Run finishedRun = run.finished(controller.clock().instant());
            locked(id.application(), id.type(), runs -> {
                runs.put(run.id(), finishedRun);
                long last = id.number();
                Iterator<RunId> ids = runs.keySet().iterator();
                for (RunId old = ids.next(); old.number() <= last - historyLength; old = ids.next()) {
                    logs.delete(old);
                    ids.remove();
                }
            });
            logs.flush(id);
            return finishedRun;
        });
    }

    /** Marks the given run as aborted; no further normal steps will run, but run-always steps will try to succeed. */
    public void abort(RunId id) {
        locked(id, run -> run.aborted());
    }

    /**
     * Accepts and stores a new application package and test jar pair under a generated application version key.
     */
    public ApplicationVersion submit(ApplicationId id, SourceRevision revision, String authorEmail, long projectId,
                                     ApplicationPackage applicationPackage, byte[] testPackageBytes) {
        AtomicReference<ApplicationVersion> version = new AtomicReference<>();
        controller.applications().lockOrThrow(id, application -> {
            if ( ! application.get().deploymentJobs().deployedInternally()) {
                // TODO jvenstad: Remove when there are no more SDv3 pipelines.
                // Copy all current packages to the new application store
                application.get().deployments().values().stream()
                           .map(Deployment::applicationVersion)
                           .distinct()
                           .forEach(appVersion -> {
                               byte[] content = controller.applications().artifacts().getApplicationPackage(application.get().id(), appVersion.id());
                               controller.applications().applicationStore().put(application.get().id(), appVersion, content);
                           });
            }

            long run = nextBuild(id);
            if (applicationPackage.compileVersion().isPresent() && applicationPackage.buildTime().isPresent())
                version.set(ApplicationVersion.from(revision, run, authorEmail,
                                                    applicationPackage.compileVersion().get(),
                                                    applicationPackage.buildTime().get()));
            else
                version.set(ApplicationVersion.from(revision, run, authorEmail));

            controller.applications().applicationStore().put(id,
                                                             version.get(),
                                                             applicationPackage.zippedContent());
            controller.applications().applicationStore().put(TesterId.of(id),
                                                             version.get(),
                                                             testPackageBytes);

            prunePackages(id);
            controller.applications().storeWithUpdatedConfig(application.withBuiltInternally(true), applicationPackage);

            controller.applications().deploymentTrigger().notifyOfCompletion(DeploymentJobs.JobReport.ofSubmission(id, projectId, version.get()));
        });
        return version.get();
    }

    /** Orders a run of the given type, or throws an IllegalStateException if that job type is already running. */
    public void start(ApplicationId id, JobType type, Versions versions) {
        controller.applications().lockIfPresent(id, application -> {
            if ( ! application.get().deploymentJobs().deployedInternally())
                throw new IllegalArgumentException(id + " is not built here!");

            locked(id, type, __ -> {
                Optional<Run> last = last(id, type);
                if (last.flatMap(run -> active(run.id())).isPresent())
                    throw new IllegalStateException("Can not start " + type + " for " + id + "; it is already running!");

                RunId newId = new RunId(id, type, last.map(run -> run.id().number()).orElse(0L) + 1);
                curator.writeLastRun(Run.initial(newId, versions, controller.clock().instant()));
            });
        });
    }

    /** Unregisters the given application and makes all associated data eligible for garbage collection. */
    public void unregister(ApplicationId id) {
        controller.applications().lockIfPresent(id, application -> {
            controller.applications().store(application.withBuiltInternally(false));
            jobs(id).forEach(type -> last(id, type).ifPresent(last -> abort(last.id())));
        });
    }

    /** Deletes run data, packages and tester deployments for applications which are unknown, or no longer built internally. */
    public void collectGarbage() {
        Set<ApplicationId> applicationsToBuild = new HashSet<>(applications());
        curator.applicationsWithJobs().stream()
               .filter(id -> ! applicationsToBuild.contains(id))
               .forEach(id -> {
                   try {
                       TesterId tester = TesterId.of(id);
                       for (JobType type : jobs(id))
                           locked(id, type, deactivateTester, __ -> {
                               try (Lock ___ = curator.lock(id, type)) {
                                   deactivateTester(tester, type);
                                   curator.deleteRunData(id, type);
                                   logs.delete(id);
                               }
                           });
                   }
                   catch (TimeoutException e) {
                       return; // Don't remove the data if we couldn't clean up all resources.
                   }
                   curator.deleteRunData(id);
               });
    }

    public void deactivateTester(TesterId id, JobType type) {
        try {
            controller.configServer().deactivate(new DeploymentId(id.id(), type.zone(controller.system())));
        }
        catch (NoInstanceException ignored) {
            // Already gone -- great!
        }
    }

    /** Returns a URI which points at a badge showing historic status of given length for the given job type for the given application. */
    public URI historicBadge(ApplicationId id, JobType type, int historyLength) {
        List<Run> runs = new ArrayList<>(runs(id, type).values());
        Run lastCompleted = null;
        if (runs.size() > 0)
            lastCompleted = runs.get(runs.size() - 1);
        if (runs.size() > 1 && ! lastCompleted.hasEnded())
            lastCompleted = runs.get(runs.size() - 2);

        return badges.historic(id, Optional.ofNullable(lastCompleted), runs.subList(Math.max(0, runs.size() - historyLength), runs.size()));
    }

    /** Returns a URI which points at a badge showing current status for all jobs for the given application. */
    public URI overviewBadge(ApplicationId id) {
        DeploymentSteps steps = new DeploymentSteps(controller.applications().require(id).deploymentSpec(), controller::system);
        return badges.overview(id,
                               steps.jobs().stream()
                                    .map(type -> last(id, type))
                                    .flatMap(Optional::stream)
                                    .collect(toList()));
    }

    /** Returns a URI of the tester endpoint retrieved from the routing generator, provided it matches an expected form. */
    Optional<URI> testerEndpoint(RunId id) {
        ApplicationId tester = id.tester().id();
        return controller.applications().getDeploymentEndpoints(new DeploymentId(tester, id.type().zone(controller.system())))
                         .flatMap(uris -> uris.stream().findAny());
    }

    // TODO jvenstad: Find a more appropriate way of doing this, at least when this is the only build service.
    private long nextBuild(ApplicationId id) {
        return 1 + controller.applications().require(id).deploymentJobs()
                             .statusOf(JobType.component)
                             .flatMap(JobStatus::lastCompleted)
                             .map(JobStatus.JobRun::id)
                             .orElse(0L);
    }

    private void prunePackages(ApplicationId id) {
        controller.applications().lockIfPresent(id, application -> {
            application.get().productionDeployments().values().stream()
                       .map(Deployment::applicationVersion)
                       .min(Comparator.comparingLong(applicationVersion -> applicationVersion.buildNumber().getAsLong()))
                       .ifPresent(oldestDeployed -> {
                           controller.applications().applicationStore().prune(id, oldestDeployed);
                           controller.applications().applicationStore().prune(TesterId.of(id), oldestDeployed);
                       });
        });
    }

    /** Locks and modifies the list of historic runs for the given application and job type. */
    private void locked(ApplicationId id, JobType type, Consumer<SortedMap<RunId, Run>> modifications) {
        try (Lock __ = curator.lock(id, type)) {
            SortedMap<RunId, Run> runs = curator.readHistoricRuns(id, type);
            modifications.accept(runs);
            curator.writeHistoricRuns(id, type, runs.values());
        }
    }

    /** Locks and modifies the run with the given id, provided it is still active. */
    private void locked(RunId id, UnaryOperator<Run> modifications) {
        try (Lock __ = curator.lock(id.application(), id.type())) {
            active(id).ifPresent(run -> {
                run = modifications.apply(run);
                curator.writeLastRun(run);
            });
        }
    }

    /** Locks the given step and checks none of its prerequisites are running, then performs the given actions. */
    public void locked(ApplicationId id, JobType type, Step step, Consumer<LockedStep> action) throws TimeoutException {
        try (Lock lock = curator.lock(id, type, step)) {
            for (Step prerequisite : step.prerequisites()) // Check that no prerequisite is still running.
                try (Lock __ = curator.lock(id, type, prerequisite)) { ; }

            action.accept(new LockedStep(lock, step));
        }
    }

}
