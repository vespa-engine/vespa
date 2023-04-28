// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.deployment;

import com.google.common.collect.ImmutableSortedMap;
import com.yahoo.component.Version;
import com.yahoo.component.VersionCompatibility;
import com.yahoo.concurrent.UncheckedTimeoutException;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.transaction.Mutex;
import com.yahoo.vespa.flags.FetchVector.Dimension;
import com.yahoo.vespa.flags.ListFlag;
import com.yahoo.vespa.flags.PermanentFlags;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.ApplicationController;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.Instance;
import com.yahoo.vespa.hosted.controller.LockedApplication;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.integration.LogEntry;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.ApplicationVersion;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RevisionId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RunId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.TestReport;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.TesterCloud;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.TesterId;
import com.yahoo.vespa.hosted.controller.application.ApplicationList;
import com.yahoo.vespa.hosted.controller.application.Change;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.application.TenantAndApplicationId;
import com.yahoo.vespa.hosted.controller.application.pkg.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.application.pkg.ApplicationPackageDiff;
import com.yahoo.vespa.hosted.controller.application.pkg.TestPackage;
import com.yahoo.vespa.hosted.controller.notification.Notification;
import com.yahoo.vespa.hosted.controller.notification.Notification.Type;
import com.yahoo.vespa.hosted.controller.notification.NotificationSource;
import com.yahoo.vespa.hosted.controller.persistence.BufferedLogStore;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;
import com.yahoo.vespa.hosted.controller.versions.VersionStatus;
import com.yahoo.vespa.hosted.controller.versions.VespaVersion;
import com.yahoo.vespa.hosted.controller.versions.VespaVersion.Confidence;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static com.yahoo.collections.Iterables.reversed;
import static com.yahoo.vespa.hosted.controller.deployment.RunStatus.aborted;
import static com.yahoo.vespa.hosted.controller.deployment.RunStatus.cancelled;
import static com.yahoo.vespa.hosted.controller.deployment.RunStatus.reset;
import static com.yahoo.vespa.hosted.controller.deployment.RunStatus.running;
import static com.yahoo.vespa.hosted.controller.deployment.Step.Status.succeeded;
import static com.yahoo.vespa.hosted.controller.deployment.Step.Status.unfinished;
import static com.yahoo.vespa.hosted.controller.deployment.Step.copyVespaLogs;
import static com.yahoo.vespa.hosted.controller.deployment.Step.deactivateTester;
import static com.yahoo.vespa.hosted.controller.deployment.Step.endStagingSetup;
import static com.yahoo.vespa.hosted.controller.deployment.Step.endTests;
import static com.yahoo.vespa.hosted.controller.deployment.Step.installInitialReal;
import static com.yahoo.vespa.hosted.controller.deployment.Step.installReal;
import static com.yahoo.vespa.hosted.controller.deployment.Step.installTester;
import static com.yahoo.vespa.hosted.controller.deployment.Step.report;
import static java.time.temporal.ChronoUnit.SECONDS;
import static java.util.Comparator.comparing;
import static java.util.Comparator.naturalOrder;
import static java.util.function.Predicate.not;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

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

    public static final Duration maxHistoryAge = Duration.ofDays(60);
    public static final Duration obsoletePackageExpiry = Duration.ofDays(7);

    private static final Logger log = Logger.getLogger(JobController.class.getName());

    private final int historyLength;
    private final Controller controller;
    private final CuratorDb curator;
    private final BufferedLogStore logs;
    private final TesterCloud cloud;
    private final JobMetrics metric;
    private final ListFlag<String> disabledZones;

    private final AtomicReference<Consumer<Run>> runner = new AtomicReference<>(__ -> { });

    public JobController(Controller controller) {
        this.historyLength = controller.system().isCd() ? 256 : 64;
        this.controller = controller;
        this.curator = controller.curator();
        this.logs = new BufferedLogStore(curator, controller.serviceRegistry().runDataStore());
        this.cloud = controller.serviceRegistry().testerCloud();
        this.metric = new JobMetrics(controller.metric());
        this.disabledZones = PermanentFlags.DISABLED_DEPLOYMENT_ZONES.bindTo(controller.flagSource());
    }

    public TesterCloud cloud() { return cloud; }
    public int historyLength() { return historyLength; }
    public void setRunner(Consumer<Run> runner) { this.runner.set(runner); }

    /** Rewrite all job data with the newest format. */
    public void updateStorage() {
        for (ApplicationId id : instances())
            for (JobType type : jobs(id)) {
                locked(id, type, runs -> { // Runs are not modified here, and are written as they were.
                    curator.readLastRun(id, type).ifPresent(curator::writeLastRun);
                });
            }
    }

    public boolean isDisabled(JobId id) {
        return disabledZones.with(Dimension.APPLICATION_ID, id.application().serializedForm()).value().contains(id.type().zone().value());
    }

    /** Returns all entries currently logged for the given run. */
    public Optional<RunLog> details(RunId id) {
        return details(id, -1);
    }

    /** Returns the logged entries for the given run, which are after the given id threshold. */
    public Optional<RunLog> details(RunId id, long after) {
        try (Mutex __ = curator.lock(id.application(), id.type())) {
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
            logs.append(id.application(), id.type(), step, entries, true);
            return __;
        });
    }

    /** Stores the given log messages for the given run and step. */
    public void log(RunId id, Step step, Level level, List<String> messages) {
        log(id, step, messages.stream()
                              .map(message -> new LogEntry(0, controller.clock().instant(), LogEntry.typeOf(level), message))
                              .toList());
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

            storeVespaLogs(id);

            // TODO jonmv: remove all the below around start of 2023.
            ZoneId zone = id.type().zone();
            Optional<Deployment> deployment = Optional.ofNullable(controller.applications().requireInstance(id.application())
                                                                            .deployments().get(zone));
            if (deployment.isEmpty() || deployment.get().at().isBefore(run.start()))
                return run;

            List<LogEntry> log;
            Optional<Instant> deployedAt;
            Instant from;
            if ( ! run.id().type().isProduction()) {
                deployedAt = run.stepInfo(installInitialReal).or(() -> run.stepInfo(installReal)).flatMap(StepInfo::startTime);
                if (deployedAt.isPresent()) {
                    from = run.lastVespaLogTimestamp().isAfter(run.start()) ? run.lastVespaLogTimestamp() : deployedAt.get().minusSeconds(10);
                    log = LogEntry.parseVespaLog(controller.serviceRegistry().configServer()
                                                           .getLogs(new DeploymentId(id.application(), zone),
                                                                    Map.of("from", Long.toString(from.toEpochMilli()))),
                                                 from);
                }
                else log = List.of();
            }
            else log = List.of();

            if (id.type().isTest()) {
                deployedAt = run.stepInfo(installTester).flatMap(StepInfo::startTime);
                if (deployedAt.isPresent()) {
                    from = run.lastVespaLogTimestamp().isAfter(run.start()) ? run.lastVespaLogTimestamp() : deployedAt.get().minusSeconds(10);
                    List<LogEntry> testerLog = LogEntry.parseVespaLog(controller.serviceRegistry().configServer()
                                                                                .getLogs(new DeploymentId(id.tester().id(), zone),
                                                                                         Map.of("from", Long.toString(from.toEpochMilli()))),
                                                                      from);

                    Instant justNow = controller.clock().instant().minusSeconds(2);
                    log = Stream.concat(log.stream(), testerLog.stream())
                                .filter(entry -> entry.at().isBefore(justNow))
                                .sorted(comparing(LogEntry::at))
                                .toList();
                }
            }
            if (log.isEmpty())
                return run;

            logs.append(id.application(), id.type(), Step.copyVespaLogs, log, false);
            return run.with(log.get(log.size() - 1).at());
        });
    }

    public InputStream getVespaLogs(RunId id, long fromMillis, boolean tester) {
        Run run = run(id);
        return run.stepStatus(copyVespaLogs).map(succeeded::equals).orElse(false)
               ? controller.serviceRegistry().runDataStore().getLogs(id, tester)
               : getVespaLogsFromLogserver(run, fromMillis, tester).orElse(InputStream.nullInputStream());
    }

    public static Optional<Instant> deploymentCompletedAt(Run run, boolean tester) {
        return (tester ? run.stepInfo(installTester)
                       : run.stepInfo(installInitialReal).or(() -> run.stepInfo(installReal)))
                .flatMap(StepInfo::startTime).map(start -> start.minusSeconds(10));
    }

    public void storeVespaLogs(RunId id) {
        Run run = run(id);
        if ( ! id.type().isProduction()) {
            getVespaLogsFromLogserver(run, 0, false).ifPresent(logs -> {
                try (logs) {
                    controller.serviceRegistry().runDataStore().putLogs(id, false, logs);
                }
                catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
        if (id.type().isTest()) {
            getVespaLogsFromLogserver(run, 0, true).ifPresent(logs -> {
                try (logs) {
                    controller.serviceRegistry().runDataStore().putLogs(id, true, logs);
                }
                catch(IOException e){
                    throw new UncheckedIOException(e);
                }
            });
        }
    }

    private Optional<InputStream> getVespaLogsFromLogserver(Run run, long fromMillis, boolean tester) {
        return deploymentCompletedAt(run, tester).map(at ->
                                                              controller.serviceRegistry().configServer().getLogs(new DeploymentId(tester ? run.id().tester().id() : run.id().application(),
                                                                                                                                   run.id().type().zone()),
                                                                                                                  Map.of("from", Long.toString(Math.max(fromMillis, at.toEpochMilli())),
                                                                                                                         "to", Long.toString(run.end().orElse(controller.clock().instant()).toEpochMilli()))));
    }

    /** Fetches any new test log entries, and records the id of the last of these, for continuation. */
    public void updateTestLog(RunId id) {
        locked(id, run -> {
            Optional<Step> step = Stream.of(endStagingSetup, endTests)
                                        .filter(run.readySteps()::contains)
                                        .findAny();
            if (step.isEmpty())
                return run;

            List<LogEntry> entries = cloud.getLog(new DeploymentId(id.tester().id(), id.type().zone()),
                                                  run.lastTestLogEntry());
            if (entries.isEmpty())
                return run;

            logs.append(id.application(), id.type(), step.get(), entries, false);
            return run.with(entries.stream().mapToLong(LogEntry::id).max().getAsLong());
        });
    }

    public void updateTestReport(RunId id) {
        locked(id, run -> {
            Optional<TestReport> report = cloud.getTestReport(new DeploymentId(id.tester().id(), id.type().zone()));
            if (report.isEmpty()) {
                return run;
            }
            logs.writeTestReport(id, report.get());
            return run;
        });
    }

    public Optional<String> getTestReports(RunId id) {
        return logs.readTestReports(id);
    }

    /** Stores the given certificate as the tester certificate for this run, or throws if it's already set. */
    public void storeTesterCertificate(RunId id, X509Certificate testerCertificate) {
        locked(id, run -> run.with(testerCertificate));
    }

    /** Returns a list of all instances of applications which have registered. */
    public List<ApplicationId> instances() {
        return controller.applications().readable().stream()
                         .flatMap(application -> application.instances().values().stream())
                         .map(Instance::id).toList();
    }

    /** Returns all job types which have been run for the given application. */
    private List<JobType> jobs(ApplicationId id) {
        return JobType.allIn(controller.zoneRegistry()).stream()
                      .filter(type -> last(id, type).isPresent()).toList();
    }

    /** Returns an immutable map of all known runs for the given application and job type. */
    public NavigableMap<RunId, Run> runs(JobId id) {
        return runs(id.application(), id.type());
    }

    /** Lists the start time of non-redeployment runs of the given job, in order of increasing age. */
    public List<Instant> jobStarts(JobId id) {
        return runs(id).descendingMap().values().stream()
                       .filter(run -> !run.isRedeployment())
                       .map(Run::start).toList();
    }

    /** Returns when given deployment last started deploying, falling back to time of deployment if it cannot be determined from job runs */
    public Instant lastDeploymentStart(ApplicationId instanceId, Deployment deployment) {
        return jobStarts(new JobId(instanceId, JobType.deploymentTo(deployment.zone()))).stream()
                                                                                        .findFirst()
                                                                                        .orElseGet(deployment::at);
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

    /** Returns the run with the given id, or throws if no such run exists. */
    public Run run(RunId id) {
        return runs(id.application(), id.type()).values().stream()
                                                .filter(run -> run.id().equals(id))
                                                .findAny()
                                                .orElseThrow(() -> new NoSuchElementException("no run with id '" + id + "' exists"));
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
                         .toList();
    }

    /** Returns a list of all active runs for the given application. */
    public List<Run> active(TenantAndApplicationId id) {
        return controller.applications().requireApplication(id).instances().keySet().stream()
                         .flatMap(name -> JobType.allIn(controller.zoneRegistry()).stream()
                                                 .map(type -> last(id.instance(name), type))
                                                 .flatMap(Optional::stream)
                                                 .filter(run -> ! run.hasEnded()))
                         .toList();
    }

    /** Returns a list of all active runs for the given instance. */
    public List<Run> active(ApplicationId id) {
        return JobType.allIn(controller.zoneRegistry()).stream()
                      .map(type -> last(id, type))
                      .flatMap(Optional::stream)
                      .filter(run -> !run.hasEnded())
                      .toList();
    }

    /** Returns the job status of the given job, possibly empty. */
    public JobStatus jobStatus(JobId id) {
        return new JobStatus(id, runs(id));
    }

    /** Returns the deployment status of the given application. */
    public DeploymentStatus deploymentStatus(Application application) {
        VersionStatus versionStatus = controller.readVersionStatus();
        return deploymentStatus(application, versionStatus, controller.systemVersion(versionStatus));
    }

    private DeploymentStatus deploymentStatus(Application application, VersionStatus versionStatus, Version systemVersion) {
        return new DeploymentStatus(application,
                                    this::jobStatus,
                                    controller.zoneRegistry(),
                                    versionStatus,
                                    systemVersion,
                                    instance -> controller.applications().versionCompatibility(application.id().instance(instance)),
                                    controller.clock().instant());
    }

    /** Adds deployment status to each of the given applications. */
    public DeploymentStatusList deploymentStatuses(ApplicationList applications, VersionStatus versionStatus) {
        Version systemVersion = controller.systemVersion(versionStatus);
        return DeploymentStatusList.from(applications.asList().stream()
                                                     .map(application -> deploymentStatus(application, versionStatus, systemVersion))
                                                     .toList());
    }

    /** Adds deployment status to each of the given applications. Calling this will do an implicit read of the controller's version status */
    public DeploymentStatusList deploymentStatuses(ApplicationList applications) {
        VersionStatus versionStatus = controller.readVersionStatus();
        return deploymentStatuses(applications, versionStatus);
    }

    /** Changes the status of the given step, for the given run, provided it is still active. */
    public void update(RunId id, RunStatus status, LockedStep step) {
        locked(id, run -> run.with(status, step));
    }

    /**
     * Changes the status of the given run to inactive, and stores it as a historic run.
     * Throws TimeoutException if some step in this job is still being run.
     */
    public void finish(RunId id) throws TimeoutException {
        Deque<Mutex> locks = new ArrayDeque<>();
        try {
            // Ensure no step is still running before we finish the run — report depends transitively on all the other steps.
            Run unlockedRun = run(id);
            locks.push(curator.lock(id.application(), id.type(), report));
            for (Step step : report.allPrerequisites(unlockedRun.steps().keySet()))
                locks.push(curator.lock(id.application(), id.type(), step));

            locked(id, run -> {
                // If run should be reset, just return here.
                if (run.status() == reset) {
                    for (Step step : run.steps().keySet())
                        log(id, step, INFO, List.of("### Run will reset, and start over at " + run.sleepUntil().orElse(controller.clock().instant()).truncatedTo(SECONDS), ""));
                    return run.reset();
                }
                if (run.status() == running && run.stepStatuses().values().stream().anyMatch(not(succeeded::equals))) return run;

                // Store the modified run after it has been written to history, in case the latter fails.
                Run finishedRun = run.finished(controller.clock().instant());
                locked(id.application(), id.type(), runs -> {
                    runs.put(run.id(), finishedRun);
                    long last = id.number();
                    long successes = runs.values().stream().filter(Run::hasSucceeded).count();
                    var oldEntries = runs.entrySet().iterator();
                    for (var old = oldEntries.next();
                         old.getKey().number() <= last - historyLength
                         || old.getValue().start().isBefore(controller.clock().instant().minus(maxHistoryAge));
                         old = oldEntries.next()) {

                        // Make sure we keep the last success and the first failing
                        if (     successes == 1
                                 &&   old.getValue().hasSucceeded()
                                 && ! old.getValue().start().isBefore(controller.clock().instant().minus(maxHistoryAge))) {
                            oldEntries.next();
                            continue;
                        }

                        logs.delete(old.getKey());
                        oldEntries.remove();
                    }
                });
                logs.flush(id);
                metric.jobFinished(run.id().job(), finishedRun.status());
                pruneRevisions(unlockedRun);

                return finishedRun;
            });
        }
        finally {
            for (Mutex lock : locks) {
                try {
                    lock.close();
                } catch (Throwable t) {
                    log.log(WARNING, "Failed to close the lock " + lock + ": the lock may or may not " +
                                     "have been released in ZooKeeper, and if not this controller " +
                                     "must be restarted to release the lock", t);
                }
            }
        }
    }

    /** Marks the given run as aborted; no further normal steps will run, but run-always steps will try to succeed. */
    public void abort(RunId id, String reason, boolean cancelledByHumans) {
        locked(id, run -> {
            if (run.status() == aborted || run.status() == cancelled)
                return run;

            run.stepStatuses().entrySet().stream()
               .filter(entry -> entry.getValue() == unfinished)
               .forEach(entry -> log(id, entry.getKey(), INFO, "Aborting run: " + reason));
            return run.aborted(cancelledByHumans);
        });
    }

    /** Accepts and stores a new application package and test jar pair under a generated application version key. */
    public ApplicationVersion submit(TenantAndApplicationId id, Submission submission, long projectId) {
        ApplicationController applications = controller.applications();
        AtomicReference<ApplicationVersion> version = new AtomicReference<>();
        applications.lockApplicationOrThrow(id, application -> {
            Optional<ApplicationVersion> previousVersion = application.get().revisions().last();
            Optional<ApplicationPackage> previousPackage = previousVersion.flatMap(previous -> applications.applicationStore().find(id.tenant(), id.application(), previous.buildNumber().getAsLong()))
                                                                          .map(ApplicationPackage::new);
            long previousBuild = previousVersion.map(latestVersion -> latestVersion.buildNumber().getAsLong()).orElse(0L);
            version.set(submission.toApplicationVersion(1 + previousBuild));

            byte[] diff = previousPackage.map(previous -> ApplicationPackageDiff.diff(previous, submission.applicationPackage()))
                                         .orElseGet(() -> ApplicationPackageDiff.diffAgainstEmpty(submission.applicationPackage()));
            applications.applicationStore().put(id.tenant(),
                                                id.application(),
                                                version.get().id(),
                                                submission.applicationPackage().zippedContent(),
                                                submission.testPackage(),
                                                diff);
            applications.applicationStore().putMeta(id.tenant(),
                                                    id.application(),
                                                    controller.clock().instant(),
                                                    submission.applicationPackage().metaDataZip());

            application = application.withProjectId(projectId == -1 ? OptionalLong.empty() : OptionalLong.of(projectId));
            application = application.withRevisions(revisions -> revisions.with(version.get()));
            application = withPrunedPackages(application, version.get().id());

            validate(id, submission);

            List<InstanceName> newInstances = applications.storeWithUpdatedConfig(application, submission.applicationPackage());
            if (application.get().projectId().isPresent())
                applications.deploymentTrigger().triggerNewRevision(id);
            for (InstanceName instance : newInstances)
                controller.applications().deploymentTrigger().forceChange(id.instance(instance), Change.of(version.get().id()));
        });
        return version.get();
    }

    private void validate(TenantAndApplicationId id, Submission submission) {
        controller.notificationsDb().removeNotification(NotificationSource.from(id), Type.testPackage);
        controller.notificationsDb().removeNotification(NotificationSource.from(id), Type.submission);

        validateTests(id, submission);
        validateMajorVersion(id, submission);
    }

    private void validateTests(TenantAndApplicationId id, Submission submission) {
        var testSummary = TestPackage.validateTests(submission.applicationPackage().deploymentSpec(), submission.testPackage());
        if ( ! testSummary.problems().isEmpty())
            controller.notificationsDb().setNotification(NotificationSource.from(id),
                                                         Type.testPackage,
                                                         Notification.Level.warning,
                                                         testSummary.problems());

    }

    private void validateMajorVersion(TenantAndApplicationId id, Submission submission) {
        submission.applicationPackage().deploymentSpec().majorVersion().ifPresent(explicitMajor -> {
            if ( ! controller.readVersionStatus().isOnCurrentMajor(new Version(explicitMajor)))
                controller.notificationsDb().setNotification(NotificationSource.from(id), Type.submission, Notification.Level.warning,
                                                             "Vespa " + explicitMajor + " will soon reach end of life, upgrade to Vespa " + (explicitMajor + 1) + " now: " +
                                                             "https://cloud.vespa.ai/en/vespa" + (explicitMajor + 1) + "-release-notes.html"); // ∠( ᐛ 」∠)＿
        });
    }

    private LockedApplication withPrunedPackages(LockedApplication application, RevisionId latest) {
        TenantAndApplicationId id = application.get().id();
        Application wrapped = application.get();
        RevisionId oldestDeployed = application.get().oldestDeployedRevision()
                                               .or(() -> wrapped.instances().values().stream()
                                                                .flatMap(instance -> instance.change().revision().stream())
                                                                .min(naturalOrder()))
                                               .orElse(latest);
        RevisionId oldestToKeep = null;
        Instant now = controller.clock().instant();
        for (ApplicationVersion version : application.get().revisions().withPackage()) {
            if (version.id().compareTo(oldestDeployed) < 0) {
                if (version.obsoleteAt().isEmpty()) {
                    application = application.withRevisions(revisions -> revisions.with(version.obsoleteAt(now)));
                    if (oldestToKeep == null)
                        oldestToKeep = version.id();
                }
                else {
                    if (oldestToKeep == null && !version.obsoleteAt().get().isBefore(now.minus(obsoletePackageExpiry)))
                        oldestToKeep = version.id();
                }
            }
        }

        if (oldestToKeep != null) {
            controller.applications().applicationStore().prune(id.tenant(), id.application(), oldestToKeep);
            for (ApplicationVersion version : application.get().revisions().withPackage())
                if (version.id().compareTo(oldestToKeep) < 0)
                    application = application.withRevisions(revisions -> revisions.with(version.withoutPackage()));
        }
        return application;
    }

    /** Forget revisions no longer present in any relevant job history. */
    private void pruneRevisions(Run run) {
        TenantAndApplicationId applicationId = TenantAndApplicationId.from(run.id().application());
        boolean isProduction = run.versions().targetRevision().isProduction();
        (isProduction ? deploymentStatus(controller.applications().requireApplication(applicationId)).jobs().asList().stream()
                      : Stream.of(jobStatus(run.id().job())))
                .flatMap(jobs -> jobs.runs().values().stream())
                .map(r -> r.versions().targetRevision())
                .filter(id -> id.isProduction() == isProduction)
                .min(naturalOrder())
                .ifPresent(oldestRevision -> {
                    controller.applications().lockApplicationOrThrow(applicationId, application -> {
                        if (isProduction) {
                            controller.applications().applicationStore().pruneDiffs(run.id().application().tenant(), run.id().application().application(), oldestRevision.number());
                            controller.applications().store(application.withRevisions(revisions -> revisions.withoutOlderThan(oldestRevision)));
                        }
                        else {
                            controller.applications().applicationStore().pruneDevDiffs(new DeploymentId(run.id().application(), run.id().job().type().zone()), oldestRevision.number());
                            controller.applications().store(application.withRevisions(revisions -> revisions.withoutOlderThan(oldestRevision, run.id().job())));
                        }
                    });
                });
    }

    /** Orders a run of the given type, or throws an IllegalStateException if that job type is already running. */
    public void start(ApplicationId id, JobType type, Versions versions, boolean isRedeployment, Optional<String> reason) {
        start(id, type, versions, isRedeployment, JobProfile.of(type), reason);
    }

    /** Orders a run of the given type, or throws an IllegalStateException if that job type is already running. */
    public void start(ApplicationId id, JobType type, Versions versions, boolean isRedeployment, JobProfile profile, Optional<String> reason) {
        ApplicationVersion revision = controller.applications().requireApplication(TenantAndApplicationId.from(id)).revisions().get(versions.targetRevision());
        if (revision.compileVersion()
                    .map(version -> controller.applications().versionCompatibility(id).refuse(versions.targetPlatform(), version))
                    .orElse(false))
            throw new IllegalArgumentException("Will not start " + type + " for " + id + " with incompatible platform version (" +
                                               versions.targetPlatform() + ") " + "and compile versions (" + revision.compileVersion().get() + ")");

        locked(id, type, __ -> {
            Optional<Run> last = last(id, type);
            if (last.flatMap(run -> active(run.id())).isPresent())
                throw new IllegalArgumentException("Cannot start " + type + " for " + id + "; it is already running!");

            RunId newId = new RunId(id, type, last.map(run -> run.id().number()).orElse(0L) + 1);
            curator.writeLastRun(Run.initial(newId, versions, isRedeployment, controller.clock().instant(), profile, reason));
            metric.jobStarted(newId.job());
        });
    }


    /** Stores the given package and starts a deployment of it, after aborting any such ongoing deployment. */
    public void deploy(ApplicationId id, JobType type, Optional<Version> platform, ApplicationPackage applicationPackage) {
        deploy(id, type, platform, applicationPackage, false, false);
    }

    /** Stores the given package and starts a deployment of it, after aborting any such ongoing deployment.*/
    public void deploy(ApplicationId id, JobType type, Optional<Version> platform, ApplicationPackage applicationPackage,
                       boolean dryRun, boolean allowOutdatedPlatform) {
        if ( ! controller.zoneRegistry().hasZone(type.zone()))
            throw new IllegalArgumentException(type.zone() + " is not present in this system");

        VersionStatus versionStatus = controller.readVersionStatus();
        if (   ! controller.system().isCd()
               &&   platform.isPresent()
               &&   versionStatus.deployableVersions().stream().map(VespaVersion::versionNumber).noneMatch(platform.get()::equals))
            throw new IllegalArgumentException("platform version " + platform.get() + " is not present in this system");

        controller.applications().lockApplicationOrThrow(TenantAndApplicationId.from(id), application -> {
            if ( ! application.get().instances().containsKey(id.instance()))
                application = controller.applications().withNewInstance(application, id);
            // TODO(mpolden): Enable for public CD once all tests have been updated
            if (controller.system() != SystemName.PublicCd) {
                controller.applications().validatePackage(applicationPackage, application.get());
                controller.applications().decideCloudAccountOf(new DeploymentId(id, type.zone()), applicationPackage.deploymentSpec());
            }
            controller.applications().store(application);
        });

        DeploymentId deploymentId = new DeploymentId(id, type.zone());
        Optional<Run> lastRun = last(id, type);
        lastRun.filter(run -> ! run.hasEnded()).ifPresent(run -> abortAndWait(run.id(), Duration.ofMinutes(2)));

        long build = 1 + lastRun.map(run -> run.versions().targetRevision().number()).orElse(0L);
        RevisionId revisionId = RevisionId.forDevelopment(build, new JobId(id, type));
        ApplicationVersion version = ApplicationVersion.forDevelopment(revisionId, applicationPackage.compileVersion(), applicationPackage.deploymentSpec().majorVersion());

        byte[] diff = getDiff(applicationPackage, deploymentId, lastRun);

        controller.applications().lockApplicationOrThrow(TenantAndApplicationId.from(id), application -> {
            Version targetPlatform = platform.orElseGet(() -> findTargetPlatform(applicationPackage, deploymentId, application.get().get(id.instance()), versionStatus));
            if (   ! allowOutdatedPlatform
                   && ! controller.readVersionStatus().isOnCurrentMajor(targetPlatform)
                   &&   runs(id, type).values().stream().noneMatch(run -> run.versions().targetPlatform().getMajor() == targetPlatform.getMajor()))
                throw new IllegalArgumentException("platform version " + targetPlatform + " is not on a current major version in this system");

            controller.applications().applicationStore().putDev(deploymentId, version.id(), applicationPackage.zippedContent(), diff);
            controller.applications().store(application.withRevisions(revisions -> revisions.with(version)));
            start(id,
                  type,
                  new Versions(targetPlatform, version.id(), lastRun.map(run -> run.versions().targetPlatform()), lastRun.map(run -> run.versions().targetRevision())),
                  false,
                  dryRun ? JobProfile.developmentDryRun : JobProfile.development,
                  Optional.empty());
        });

        locked(id, type, __ -> {
            runner.get().accept(last(id, type).get());
        });
    }

    /* Application package diff against previous version, or against empty version if previous does not exist or is invalid */
    private byte[] getDiff(ApplicationPackage applicationPackage, DeploymentId deploymentId, Optional<Run> lastRun) {
        return lastRun.map(run -> run.versions().targetRevision())
                      .map(prevVersion -> {
                          ApplicationPackage previous;
                          try {
                              previous = new ApplicationPackage(controller.applications().applicationStore().get(deploymentId, prevVersion));
                          } catch (IllegalArgumentException e) {
                              return ApplicationPackageDiff.diffAgainstEmpty(applicationPackage);
                          }
                          return ApplicationPackageDiff.diff(previous, applicationPackage);
                      })
                      .orElseGet(() -> ApplicationPackageDiff.diffAgainstEmpty(applicationPackage));
    }

    private Version findTargetPlatform(ApplicationPackage applicationPackage, DeploymentId id, Optional<Instance> instance, VersionStatus versionStatus) {
        // Prefer previous platform if possible. Candidates are all deployable, ascending, with existing version appended; then reversed.
        Version systemVersion = controller.systemVersion(versionStatus);

        List<Version> versions = new ArrayList<>(List.of(systemVersion));
        for (VespaVersion version : versionStatus.deployableVersions())
            if (version.confidence().equalOrHigherThan(Confidence.normal))
                versions.add(version.versionNumber());

        instance.map(Instance::deployments)
                .map(deployments -> deployments.get(id.zoneId()))
                .map(Deployment::version)
                .filter(versions::contains) // Don't deploy versions that are no longer known.
                .ifPresent(versions::add);

        // Remove all versions that are older than the compile version.
        versions.removeIf(version -> applicationPackage.compileVersion().map(version::isBefore).orElse(false));
        if (versions.isEmpty()) {
            // Fall back to the newest deployable version, if all the ones with normal confidence were too old.
            Iterator<VespaVersion> descending = reversed(versionStatus.deployableVersions()).iterator();
            if ( ! descending.hasNext())
                throw new IllegalStateException("no deployable platform version found in the system");
            else
                versions.add(descending.next().versionNumber());
        }

        VersionCompatibility compatibility = controller.applications().versionCompatibility(id.applicationId());
        List<Version> compatibleVersions = new ArrayList<>();
        for (Version target : reversed(versions))
            if (applicationPackage.compileVersion().isEmpty() || compatibility.accept(target, applicationPackage.compileVersion().get()))
                compatibleVersions.add(target);

        if (compatibleVersions.isEmpty())
            throw new IllegalArgumentException("no platforms are compatible with compile version " + applicationPackage.compileVersion().get());

        Optional<Integer> major = applicationPackage.deploymentSpec().majorVersion();
        List<Version> versionOnRightMajor = new ArrayList<>();
        for (Version target : reversed(versions))
            if (major.isEmpty() || major.get() == target.getMajor())
                versionOnRightMajor.add(target);

        if (versionOnRightMajor.isEmpty())
            throw new IllegalArgumentException("no platforms were found for major version " + major.get() + " specified in deployment.xml");

        for (Version target : compatibleVersions)
            if (versionOnRightMajor.contains(target))
                return target;

        throw new IllegalArgumentException("no platforms on major version " + major.get() + " specified in deployment.xml " +
                                           "are compatible with compile version " + applicationPackage.compileVersion().get());
    }

    /** Aborts a run and waits for it complete. */
    private void abortAndWait(RunId id, Duration timeout) {
        abort(id, "replaced by new deployment", true);
        runner.get().accept(last(id.application(), id.type()).get());

        Instant doom = controller.clock().instant().plus(timeout);
        Duration sleep = Duration.ofMillis(100);
        while ( ! last(id.application(), id.type()).get().hasEnded()) {
            if (controller.clock().instant().plus(sleep).isAfter(doom))
                throw new UncheckedTimeoutException("timeout waiting for " + id + " to abort and finish");
            try {
                Thread.sleep(sleep.toMillis());
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
                               try (Mutex ___ = curator.lock(id, type)) {
                                   try {
                                       deactivateTester(tester, type);
                                   }
                                   catch (Exception e) {
                                       // It's probably already deleted, so if we fail, that's OK.
                                   }
                                   curator.deleteRunData(id, type);
                               }
                           });
                       logs.delete(id);
                       curator.deleteRunData(id);
                   }
                   catch (Exception e) {
                       log.log(WARNING, "failed cleaning up after deleted application", e);
                   }
               });
    }

    public void deactivateTester(TesterId id, JobType type) {
        controller.serviceRegistry().configServer().deactivate(new DeploymentId(id.id(), type.zone()));
    }

    /** Locks all runs and modifies the list of historic runs for the given application and job type. */
    private void locked(ApplicationId id, JobType type, Consumer<SortedMap<RunId, Run>> modifications) {
        try (Mutex __ = curator.lock(id, type)) {
            SortedMap<RunId, Run> runs = new TreeMap<>(curator.readHistoricRuns(id, type));
            modifications.accept(runs);
            curator.writeHistoricRuns(id, type, runs.values());
        }
    }

    /** Locks and modifies the run with the given id, provided it is still active. */
    public void locked(RunId id, UnaryOperator<Run> modifications) {
        try (Mutex __ = curator.lock(id.application(), id.type())) {
            active(id).ifPresent(run -> {
                Run modified = modifications.apply(run);
                if (modified != null) curator.writeLastRun(modified);
            });
        }
    }

    /** Locks the given step and checks none of its prerequisites are running, then performs the given actions. */
    public void locked(ApplicationId id, JobType type, Step step, Consumer<LockedStep> action) throws TimeoutException {
        try (Mutex lock = curator.lock(id, type, step)) {
            for (Step prerequisite : step.allPrerequisites(last(id, type).get().steps().keySet())) // Check that no prerequisite is still running.
                try (Mutex __ = curator.lock(id, type, prerequisite)) { ; }

            action.accept(new LockedStep(lock, step));
        }
    }

}
