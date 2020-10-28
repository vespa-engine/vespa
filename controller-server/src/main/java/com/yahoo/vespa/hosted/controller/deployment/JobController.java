// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.deployment;

import com.google.common.collect.ImmutableSortedMap;
import com.yahoo.component.Version;
import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.curator.Lock;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.Instance;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.integration.LogEntry;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.NotFoundException;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.ApplicationVersion;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RunId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.SourceRevision;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.TestReport;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.TesterCloud;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.TesterId;
import com.yahoo.vespa.hosted.controller.application.ApplicationList;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.application.TenantAndApplicationId;
import com.yahoo.vespa.hosted.controller.persistence.BufferedLogStore;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;

import java.net.URI;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.SortedMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.logging.Level;
import java.util.stream.Stream;

import static com.google.common.collect.ImmutableList.copyOf;
import static com.yahoo.vespa.hosted.controller.deployment.Step.copyVespaLogs;
import static com.yahoo.vespa.hosted.controller.deployment.Step.deactivateTester;
import static com.yahoo.vespa.hosted.controller.deployment.Step.endStagingSetup;
import static com.yahoo.vespa.hosted.controller.deployment.Step.endTests;
import static com.yahoo.vespa.hosted.controller.deployment.Step.report;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toUnmodifiableList;

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

    public static final int historyLength = 64;
    public static final Duration maxHistoryAge = Duration.ofDays(60);

    private final Controller controller;
    private final CuratorDb curator;
    private final BufferedLogStore logs;
    private final TesterCloud cloud;
    private final Badges badges;
    private final JobMetrics metric;

    private final AtomicReference<Consumer<Run>> runner = new AtomicReference<>(__ -> { });

    public JobController(Controller controller) {
        this.controller = controller;
        this.curator = controller.curator();
        this.logs = new BufferedLogStore(curator, controller.serviceRegistry().runDataStore());
        this.cloud = controller.serviceRegistry().testerCloud();
        this.badges = new Badges(controller.zoneRegistry().badgeUrl());
        this.metric = new JobMetrics(controller.metric(), controller.system());
    }

    public TesterCloud cloud() { return cloud; }
    public int historyLength() { return historyLength; }
    public void setRunner(Consumer<Run> runner) { this.runner.set(runner); }

    /** Rewrite all job data with the newest format. */
    public void updateStorage() {
        for (ApplicationId id : instances())
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

    /** Stores the given log entries for the given run and step. */
    public void log(RunId id, Step step, List<LogEntry> entries) {
        locked(id, __ -> {
            logs.append(id.application(), id.type(), step, entries);
            return __;
        });
    }

    /** Stores the given log messages for the given run and step. */
    public void log(RunId id, Step step, Level level, List<String> messages) {
        log(id, step, messages.stream()
                              .map(message -> new LogEntry(0, controller.clock().instant(), LogEntry.typeOf(level), message))
                              .collect(toList()));
    }

    /** Stores the given log message for the given run and step. */
    public void log(RunId id, Step step, Level level, String message) {
        log(id, step, level, Collections.singletonList(message));
    }

    /** Fetches any new Vespa log entries, and records the timestamp of the last of these, for continuation. */
    public void updateVespaLog(RunId id) {
        locked(id, run -> {
            if ( ! run.hasStep(copyVespaLogs))
                return run;

            ZoneId zone = id.type().zone(controller.system());
            Optional<Deployment> deployment = Optional.ofNullable(controller.applications().requireInstance(id.application())
                                                                            .deployments().get(zone));
            if (deployment.isEmpty() || deployment.get().at().isBefore(run.start()))
                return run;

            Instant from = run.lastVespaLogTimestamp().isAfter(deployment.get().at()) ? run.lastVespaLogTimestamp() : deployment.get().at();
            List<LogEntry> log = LogEntry.parseVespaLog(controller.serviceRegistry().configServer()
                                                                  .getLogs(new DeploymentId(id.application(), zone),
                                                                           Map.of("from", Long.toString(from.toEpochMilli()))),
                                                        from);
            if (log.isEmpty())
                return run;

            logs.append(id.application(), id.type(), Step.copyVespaLogs, log);
            return run.with(log.get(log.size() - 1).at());
        });
    }

    /** Fetches any new test log entries, and records the id of the last of these, for continuation. */
    public void updateTestLog(RunId id) {
        locked(id, run -> {
            Optional<Step> step = Stream.of(endStagingSetup, endTests)
                                        .filter(run.readySteps()::contains)
                                        .findAny();
            if (step.isEmpty())
                return run;

            List<LogEntry> entries = cloud.getLog(new DeploymentId(id.tester().id(), id.type().zone(controller.system())),
                                                  run.lastTestLogEntry());
            if (entries.isEmpty())
                return run;

            logs.append(id.application(), id.type(), step.get(), entries);
            return run.with(entries.stream().mapToLong(LogEntry::id).max().getAsLong());
        });
    }

    public void updateTestReport(RunId id) {
        locked(id, run -> {
            Optional<TestReport> report = cloud.getTestReport(new DeploymentId(id.tester().id(), id.type().zone(controller.system())));
            if (report.isEmpty()) {
                return run;
            }
            logs.writeTestReport(id, report.get());
            return run;
        });
    }

    public Optional<String> getTestReport(RunId id) {
        return logs.readTestReport(id);
    }

    /** Stores the given certificate as the tester certificate for this run, or throws if it's already set. */
    public void storeTesterCertificate(RunId id, X509Certificate testerCertificate) {
        locked(id, run -> run.with(testerCertificate));
    }

    /** Returns a list of all instances of applications which have registered. */
    public List<ApplicationId> instances() {
        return copyOf(controller.applications().readable().stream()
                                .flatMap(application -> application.instances().values().stream())
                                .map(Instance::id)
                                .iterator());
    }

    /** Returns all job types which have been run for the given application. */
    public List<JobType> jobs(ApplicationId id) {
        return copyOf(Stream.of(JobType.values())
                            .filter(type -> last(id, type).isPresent())
                            .iterator());
    }

    /** Returns an immutable map of all known runs for the given application and job type. */
    public NavigableMap<RunId, Run> runs(JobId id) {
        return runs(id.application(), id.type());
    }

    /** Returns an immutable map of all known runs for the given application and job type. */
    public NavigableMap<RunId, Run> runs(ApplicationId id, JobType type) {
        ImmutableSortedMap.Builder<RunId, Run> runs = ImmutableSortedMap.orderedBy(Comparator.comparing(RunId::number));
        Optional<Run> last = last(id, type);
        curator.readHistoricRuns(id, type).forEach((runId, run) -> {
            if (last.isEmpty() || ! runId.equals(last.get().id()))
                runs.put(runId, run);
        });
        last.ifPresent(run -> runs.put(run.id(), run));
        return runs.build();
    }

    /** Returns the run with the given id, if it exists. */
    public Optional<Run> run(RunId id) {
        return runs(id.application(), id.type()).values().stream()
                                                .filter(run -> run.id().equals(id))
                                                .findAny();
    }

    /** Returns the last run of the given type, for the given application, if one has been run. */
    public Optional<Run> last(JobId job) {
        return curator.readLastRun(job.application(), job.type());
    }

    /** Returns the last run of the given type, for the given application, if one has been run. */
    public Optional<Run> last(ApplicationId id, JobType type) {
        return curator.readLastRun(id, type);
    }

    /** Returns the last completed of the given job. */
    public Optional<Run> lastCompleted(JobId id) {
        return JobStatus.lastCompleted(runs(id));
    }

    /** Returns the first failing of the given job. */
    public Optional<Run> firstFailing(JobId id) {
        return JobStatus.firstFailing(runs(id));
    }

    /** Returns the last success of the given job. */
    public Optional<Run> lastSuccess(JobId id) {
        return JobStatus.lastSuccess(runs(id));
    }

    /** Returns the run with the given id, provided it is still active. */
    public Optional<Run> active(RunId id) {
        return last(id.application(), id.type())
                .filter(run -> ! run.hasEnded())
                .filter(run -> run.id().equals(id));
    }

    /** Returns a list of all active runs. */
    public List<Run> active() {
        return controller.applications().idList().stream()
                         .flatMap(id -> active(id).stream())
                         .collect(toUnmodifiableList());
    }

    /** Returns a list of all active runs for the given application. */
    public List<Run> active(TenantAndApplicationId id) {
        return copyOf(controller.applications().requireApplication(id).instances().keySet().stream()
                                .flatMap(name -> Stream.of(JobType.values())
                                                       .map(type -> last(id.instance(name), type))
                                                       .flatMap(Optional::stream)
                                                       .filter(run -> ! run.hasEnded()))
                                .iterator());
    }

    /** Returns a list of all active runs for the given instance. */
    public List<Run> active(ApplicationId id) {
        return copyOf(Stream.of(JobType.values())
                            .map(type -> last(id, type))
                            .flatMap(Optional::stream)
                            .filter(run -> ! run.hasEnded())
                            .iterator());
    }

    /** Returns the job status of the given job, possibly empty. */
    public JobStatus jobStatus(JobId id) {
        return new JobStatus(id, runs(id));
    }

    /** Returns the deployment status of the given application. */
    public DeploymentStatus deploymentStatus(Application application) {
        return deploymentStatus(application, controller.readSystemVersion());
    }

    private DeploymentStatus deploymentStatus(Application application, Version systemVersion) {
        return new DeploymentStatus(application,
                                    DeploymentStatus.jobsFor(application, controller.system()).stream()
                                                    .collect(toMap(job -> job,
                                                                   job -> jobStatus(job),
                                                                   (j1, j2) -> { throw new IllegalArgumentException("Duplicate key " + j1.id()); },
                                                                   LinkedHashMap::new)),
                                    controller.system(),
                                    systemVersion,
                                    controller.clock().instant());
    }

    /** Adds deployment status to each of the given applications. */
    public DeploymentStatusList deploymentStatuses(ApplicationList applications, Version systemVersion) {
        return DeploymentStatusList.from(applications.asList().stream()
                                                     .map(application -> deploymentStatus(application, systemVersion))
                                                     .collect(toUnmodifiableList()));
    }

    /** Adds deployment status to each of the given applications. Calling this will do an implicit read of the controller's version status */
    public DeploymentStatusList deploymentStatuses(ApplicationList applications) {
        return deploymentStatuses(applications, controller.readSystemVersion());
    }

    /** Changes the status of the given step, for the given run, provided it is still active. */
    public void update(RunId id, RunStatus status, LockedStep step) {
        locked(id, run -> run.with(status, step));
    }

    /** Invoked when starting the step */
    public void setStartTimestamp(RunId id, Instant timestamp, LockedStep step) {
        locked(id, run -> run.with(timestamp, step));
    }

    /**
     * Changes the status of the given run to inactive, and stores it as a historic run.
     * Throws TimeoutException if some step in this job is still being run.
     */
    public void finish(RunId id) throws TimeoutException {
        List<Lock> locks = new ArrayList<>();
        try {
            // Ensure no step is still running before we finish the run — report depends transitively on all the other steps.
            for (Step step : report.allPrerequisites())
                locks.add(curator.lock(id.application(), id.type(), step));

            locked(id, run -> { // Store the modified run after it has been written to history, in case the latter fails.
                Run finishedRun = run.finished(controller.clock().instant());
                locked(id.application(), id.type(), runs -> {
                    runs.put(run.id(), finishedRun);
                    long last = id.number();
                    long successes = runs.values().stream().filter(old -> old.status() == RunStatus.success).count();
                    var oldEntries = runs.entrySet().iterator();
                    for (var old = oldEntries.next();
                         old.getKey().number() <= last - historyLength
                         || old.getValue().start().isBefore(controller.clock().instant().minus(maxHistoryAge));
                         old = oldEntries.next()) {

                        // Make sure we keep the last success and the first failing
                        if (successes == 1
                            && old.getValue().status() == RunStatus.success
                            && !old.getValue().start().isBefore(controller.clock().instant().minus(maxHistoryAge))) {
                            oldEntries.next();
                            continue;
                        }

                        logs.delete(old.getKey());
                        oldEntries.remove();
                    }
                });
                logs.flush(id);
                metric.jobFinished(run.id().job(), finishedRun.status());
                return finishedRun;
            });
        }
        finally {
            for (Lock lock : locks)
                lock.close();
        }
    }

    /** Marks the given run as aborted; no further normal steps will run, but run-always steps will try to succeed. */
    public void abort(RunId id) {
        locked(id, run -> run.aborted());
    }

    /** Accepts and stores a new application package and test jar pair under a generated application version key. */
    public ApplicationVersion submit(TenantAndApplicationId id, Optional<SourceRevision> revision, Optional<String> authorEmail,
                                     Optional<String> sourceUrl, long projectId, ApplicationPackage applicationPackage,
                                     byte[] testPackageBytes) {
        AtomicReference<ApplicationVersion> version = new AtomicReference<>();
        controller.applications().lockApplicationOrThrow(id, application -> {
            long run = 1 + application.get().latestVersion()
                                      .map(latestVersion -> latestVersion.buildNumber().getAsLong())
                                      .orElse(0L);
            version.set(ApplicationVersion.from(revision, run, authorEmail,
                                                applicationPackage.compileVersion(),
                                                applicationPackage.buildTime(),
                                                sourceUrl,
                                                revision.map(SourceRevision::commit)));

            controller.applications().applicationStore().put(id.tenant(),
                                                             id.application(),
                                                             version.get(),
                                                             applicationPackage.zippedContent());
            controller.applications().applicationStore().putTester(id.tenant(),
                                                                   id.application(),
                                                                   version.get(),
                                                                   testPackageBytes);
            controller.applications().applicationStore().putMeta(id.tenant(),
                                                                 id.application(),
                                                                 controller.clock().instant(),
                                                                 applicationPackage.metaDataZip());

            prunePackages(id);
            controller.applications().storeWithUpdatedConfig(application, applicationPackage);

            controller.applications().deploymentTrigger().notifyOfSubmission(id, version.get(), projectId);
        });
        return version.get();
    }

    /** Orders a run of the given type, or throws an IllegalStateException if that job type is already running. */
    public void start(ApplicationId id, JobType type, Versions versions) {
        start(id, type, versions, JobProfile.of(type));
    }

    /** Orders a run of the given type, or throws an IllegalStateException if that job type is already running. */
    public void start(ApplicationId id, JobType type, Versions versions, JobProfile profile) {
        locked(id, type, __ -> {
            Optional<Run> last = last(id, type);
            if (last.flatMap(run -> active(run.id())).isPresent())
                throw new IllegalStateException("Can not start " + type + " for " + id + "; it is already running!");

            RunId newId = new RunId(id, type, last.map(run -> run.id().number()).orElse(0L) + 1);
            curator.writeLastRun(Run.initial(newId, versions, controller.clock().instant(), profile));
            metric.jobStarted(newId.job());
        });
    }

    /** Stores the given package and starts a deployment of it, after aborting any such ongoing deployment. */
    public void deploy(ApplicationId id, JobType type, Optional<Version> platform, ApplicationPackage applicationPackage) {
        controller.applications().lockApplicationOrThrow(TenantAndApplicationId.from(id), application -> {
            if ( ! application.get().instances().containsKey(id.instance()))
                application = controller.applications().withNewInstance(application, id);

            controller.applications().store(application);
        });

        last(id, type).filter(run -> ! run.hasEnded()).ifPresent(run -> abortAndWait(run.id()));

        controller.applications().lockApplicationOrThrow(TenantAndApplicationId.from(id), application -> {
            controller.applications().applicationStore().putDev(id, type.zone(controller.system()), applicationPackage.zippedContent());
            start(id,
                  type,
                  new Versions(platform.orElse(applicationPackage.deploymentSpec().majorVersion()
                                                                 .flatMap(controller.applications()::lastCompatibleVersion)
                                                                 .orElseGet(controller::readSystemVersion)),
                               ApplicationVersion.unknown,
                               Optional.empty(),
                               Optional.empty()),
                  JobProfile.development);
        });

        locked(id, type, __ -> {
            runner.get().accept(last(id, type).get());
        });
    }

    /** Aborts a run and waits for it complete. */
    private void abortAndWait(RunId id) {
        abort(id);
        runner.get().accept(last(id.application(), id.type()).get());

        while ( ! last(id.application(), id.type()).get().hasEnded()) {
            try {
                Thread.sleep(100);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }
    }

    /** Deletes run data and tester deployments for applications which are unknown, or no longer built internally. */
    public void collectGarbage() {
        Set<ApplicationId> applicationsToBuild = new HashSet<>(instances());
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

    // TODO(mpolden): Eliminate duplication in this and ApplicationController#deactivate
    public void deactivateTester(TesterId id, JobType type) {
        var zone = type.zone(controller.system());
        try {
            controller.serviceRegistry().configServer().deactivate(new DeploymentId(id.id(), zone));
        } catch (NotFoundException ignored) {
            // Already gone -- great!
        } finally {
            // Passing an empty DeploymentSpec here is fine as it's used for registering global endpoint names, and
            // tester instances have none.
            controller.routing().policies().refresh(id.id(), DeploymentSpec.empty, zone);
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
        DeploymentSteps steps = new DeploymentSteps(controller.applications().requireApplication(TenantAndApplicationId.from(id))
                                                              .deploymentSpec().requireInstance(id.instance()),
                                                    controller::system);
        return badges.overview(id,
                               steps.jobs().stream()
                                    .map(type -> last(id, type))
                                    .flatMap(Optional::stream)
                                    .collect(toList()));
    }

    private void prunePackages(TenantAndApplicationId id) {
        controller.applications().lockApplicationIfPresent(id, application -> {
            application.get().productionDeployments().values().stream()
                       .flatMap(List::stream)
                       .map(Deployment::applicationVersion)
                       .filter(version -> ! version.isUnknown())
                       .min(Comparator.comparingLong(applicationVersion -> applicationVersion.buildNumber().getAsLong()))
                       .ifPresent(oldestDeployed -> {
                           controller.applications().applicationStore().prune(id.tenant(), id.application(), oldestDeployed);
                           controller.applications().applicationStore().pruneTesters(id.tenant(), id.application(), oldestDeployed);
                       });
        });
    }

    /** Locks all runs and modifies the list of historic runs for the given application and job type. */
    private void locked(ApplicationId id, JobType type, Consumer<SortedMap<RunId, Run>> modifications) {
        try (Lock __ = curator.lock(id, type)) {
            SortedMap<RunId, Run> runs = curator.readHistoricRuns(id, type);
            modifications.accept(runs);
            curator.writeHistoricRuns(id, type, runs.values());
        }
    }

    /** Locks and modifies the run with the given id, provided it is still active. */
    public void locked(RunId id, UnaryOperator<Run> modifications) {
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
            for (Step prerequisite : step.allPrerequisites()) // Check that no prerequisite is still running.
                try (Lock __ = curator.lock(id, type, prerequisite)) { ; }

            action.accept(new LockedStep(lock, step));
        }
    }

}
