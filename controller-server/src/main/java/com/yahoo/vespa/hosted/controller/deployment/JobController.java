// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.deployment;

import com.google.common.collect.ImmutableSortedMap;
import com.yahoo.component.Version;
import com.yahoo.component.VersionCompatibility;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.transaction.Mutex;
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
import com.yahoo.vespa.hosted.controller.api.integration.deployment.SourceRevision;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.TestReport;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.TesterCloud;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.TesterId;
import com.yahoo.vespa.hosted.controller.application.ApplicationList;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.application.TenantAndApplicationId;
import com.yahoo.vespa.hosted.controller.application.pkg.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.application.pkg.ApplicationPackageDiff;
import com.yahoo.vespa.hosted.controller.persistence.BufferedLogStore;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;
import com.yahoo.vespa.hosted.controller.versions.VersionStatus;
import com.yahoo.vespa.hosted.controller.versions.VespaVersion;

import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
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
import static com.yahoo.vespa.hosted.controller.deployment.RunStatus.reset;
import static com.yahoo.vespa.hosted.controller.deployment.RunStatus.running;
import static com.yahoo.vespa.hosted.controller.deployment.Step.Status.succeeded;
import static com.yahoo.vespa.hosted.controller.deployment.Step.Status.unfinished;
import static com.yahoo.vespa.hosted.controller.deployment.Step.copyVespaLogs;
import static com.yahoo.vespa.hosted.controller.deployment.Step.deactivateTester;
import static com.yahoo.vespa.hosted.controller.deployment.Step.endStagingSetup;
import static com.yahoo.vespa.hosted.controller.deployment.Step.endTests;
import static com.yahoo.vespa.hosted.controller.deployment.Step.report;
import static java.time.temporal.ChronoUnit.SECONDS;
import static java.util.Comparator.naturalOrder;
import static java.util.function.Predicate.not;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static java.util.stream.Collectors.toList;
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

    public static final Duration maxHistoryAge = Duration.ofDays(60);

    private static final Logger log = Logger.getLogger(JobController.class.getName());

    private final int historyLength;
    private final Controller controller;
    private final CuratorDb curator;
    private final BufferedLogStore logs;
    private final TesterCloud cloud;
    private final JobMetrics metric;

    private final AtomicReference<Consumer<Run>> runner = new AtomicReference<>(__ -> { });

    public JobController(Controller controller) {
        this.historyLength = controller.system().isCd() ? 256 : 64;
        this.controller = controller;
        this.curator = controller.curator();
        this.logs = new BufferedLogStore(curator, controller.serviceRegistry().runDataStore());
        this.cloud = controller.serviceRegistry().testerCloud();
        this.metric = new JobMetrics(controller.metric(), controller::system);
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

            ZoneId zone = id.type().zone();
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

            List<LogEntry> entries = cloud.getLog(new DeploymentId(id.tester().id(), id.type().zone()),
                                                  run.lastTestLogEntry());
            if (entries.isEmpty())
                return run;

            logs.append(id.application(), id.type(), step.get(), entries);
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
                         .map(Instance::id)
                         .collect(toUnmodifiableList());
    }

    /** Returns all job types which have been run for the given application. */
    private List<JobType> jobs(ApplicationId id) {
        return JobType.allIn(controller.zoneRegistry()).stream()
                      .filter(type -> last(id, type).isPresent())
                      .collect(toUnmodifiableList());
    }

    /** Returns an immutable map of all known runs for the given application and job type. */
    public NavigableMap<RunId, Run> runs(JobId id) {
        return runs(id.application(), id.type());
    }

    /** Lists the start time of non-redeployment runs of the given job, in order of increasing age. */
    public List<Instant> jobStarts(JobId id) {
        return runs(id).descendingMap().values().stream()
                       .filter(run -> ! run.isRedeployment())
                       .map(Run::start)
                       .collect(toUnmodifiableList());
    }

    /** Returns when given deployment last started deploying, falling back to time of deployment if it cannot be determined from job runs */
    public Instant lastDeploymentStart(ApplicationId instanceId, Deployment deployment) {
        return jobStarts(new JobId(instanceId, JobType.from(controller.system(),
                                                            deployment.zone()).get())).stream()
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
        return controller.applications().requireApplication(id).instances().keySet().stream()
                         .flatMap(name -> JobType.allIn(controller.zoneRegistry()).stream()
                                                 .map(type -> last(id.instance(name), type))
                                                 .flatMap(Optional::stream)
                                                 .filter(run -> ! run.hasEnded()))
                         .collect(toUnmodifiableList());
    }

    /** Returns a list of all active runs for the given instance. */
    public List<Run> active(ApplicationId id) {
        return JobType.allIn(controller.zoneRegistry()).stream()
                      .map(type -> last(id, type))
                      .flatMap(Optional::stream)
                      .filter(run -> !run.hasEnded())
                      .collect(toUnmodifiableList());
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
                                    this::jobStatus,
                                    controller.zoneRegistry(),
                                    systemVersion,
                                    instance -> controller.applications().versionCompatibility(application.id().instance(instance)),
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
        List<Mutex> locks = new ArrayList<>();
        try {
            // Ensure no step is still running before we finish the run — report depends transitively on all the other steps.
            Run unlockedRun = run(id).get();
            for (Step step : report.allPrerequisites(unlockedRun.steps().keySet()))
                locks.add(curator.lock(id.application(), id.type(), step));

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
            for (Mutex lock : locks)
                lock.close();
        }
    }

    /** Marks the given run as aborted; no further normal steps will run, but run-always steps will try to succeed. */
    public void abort(RunId id, String reason) {
        locked(id, run -> {
            run.stepStatuses().entrySet().stream()
               .filter(entry -> entry.getValue() == unfinished)
               .forEach(entry -> log(id, entry.getKey(), INFO, "Aborting run: " + reason));
            return run.aborted();
        });
    }

    /** Accepts and stores a new application package and test jar pair under a generated application version key. */
    public ApplicationVersion submit(TenantAndApplicationId id, Optional<SourceRevision> revision, Optional<String> authorEmail,
                                     Optional<String> sourceUrl, long projectId, ApplicationPackage applicationPackage,
                                     byte[] testPackageBytes, Optional<String> description, int risk) {
        ApplicationController applications = controller.applications();
        AtomicReference<ApplicationVersion> version = new AtomicReference<>();
        applications.lockApplicationOrThrow(id, application -> {
            Optional<ApplicationVersion> previousVersion = application.get().revisions().last();
            Optional<ApplicationPackage> previousPackage = previousVersion.flatMap(previous -> applications.applicationStore().find(id.tenant(), id.application(), previous.buildNumber().getAsLong()))
                                                                          .map(ApplicationPackage::new);
            long previousBuild = previousVersion.map(latestVersion -> latestVersion.buildNumber().getAsLong()).orElse(0L);
            String packageHash = applicationPackage.bundleHash() + ApplicationPackage.calculateHash(testPackageBytes);
            RevisionId revisionId = RevisionId.forProduction(1 + previousBuild);
            version.set(ApplicationVersion.forProduction(revisionId,
                                                         revision,
                                                         authorEmail,
                                                         applicationPackage.compileVersion(),
                                                         applicationPackage.buildTime(),
                                                         sourceUrl,
                                                         revision.map(SourceRevision::commit),
                                                         Optional.of(packageHash),
                                                         description,
                                                         risk));

            byte[] diff = previousPackage.map(previous -> ApplicationPackageDiff.diff(previous, applicationPackage))
                                         .orElseGet(() -> ApplicationPackageDiff.diffAgainstEmpty(applicationPackage));
            applications.applicationStore().put(id.tenant(),
                                                id.application(),
                                                version.get().id(),
                                                applicationPackage.zippedContent(),
                                                testPackageBytes,
                                                diff);
            applications.applicationStore().putMeta(id.tenant(),
                                                    id.application(),
                                                    controller.clock().instant(),
                                                    applicationPackage.metaDataZip());

            application = application.withProjectId(projectId == -1 ? OptionalLong.empty() : OptionalLong.of(projectId));
            application = application.withRevisions(revisions -> revisions.with(version.get()));
            application = withPrunedPackages(application);

            applications.storeWithUpdatedConfig(application, applicationPackage);
            applications.deploymentTrigger().triggerNewRevision(id);
        });
        return version.get();
    }

    private LockedApplication withPrunedPackages(LockedApplication application){
        TenantAndApplicationId id = application.get().id();
        Optional<RevisionId> oldestDeployed = application.get().oldestDeployedRevision();
        if (oldestDeployed.isPresent()) {
            controller.applications().applicationStore().prune(id.tenant(), id.application(), oldestDeployed.get());

            for (ApplicationVersion version : application.get().revisions().withPackage())
                if (version.id().compareTo(oldestDeployed.get()) < 0)
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
            throw new IllegalArgumentException("Will not start a job with incompatible platform version (" + versions.targetPlatform() + ") " +
                                               "and compile versions (" + revision.compileVersion().get() + ")");

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
        deploy(id, type, platform, applicationPackage, false);
    }

    /** Stores the given package and starts a deployment of it, after aborting any such ongoing deployment.*/
    public void deploy(ApplicationId id, JobType type, Optional<Version> platform, ApplicationPackage applicationPackage, boolean dryRun) {
        if ( ! controller.zoneRegistry().hasZone(type.zone()))
            throw new IllegalArgumentException(type.zone() + " is not present in this system");

        controller.applications().lockApplicationOrThrow(TenantAndApplicationId.from(id), application -> {
            if ( ! application.get().instances().containsKey(id.instance()))
                application = controller.applications().withNewInstance(application, id);

            controller.applications().store(application);
        });

        DeploymentId deploymentId = new DeploymentId(id, type.zone());
        Optional<Run> lastRun = last(id, type);
        lastRun.filter(run -> ! run.hasEnded()).ifPresent(run -> abortAndWait(run.id()));

        long build = 1 + lastRun.map(run -> run.versions().targetRevision().number()).orElse(0L);
        RevisionId revisionId = RevisionId.forDevelopment(build, new JobId(id, type));
        ApplicationVersion version = ApplicationVersion.forDevelopment(revisionId, applicationPackage.compileVersion());

        byte[] diff = getDiff(applicationPackage, deploymentId, lastRun);

        controller.applications().lockApplicationOrThrow(TenantAndApplicationId.from(id), application -> {
            controller.applications().applicationStore().putDev(deploymentId, version.id(), applicationPackage.zippedContent(), diff);
            Version targetPlatform = platform.orElseGet(() -> findTargetPlatform(applicationPackage, lastRun, id));
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

    private Version findTargetPlatform(ApplicationPackage applicationPackage, Optional<Run> lastRun, ApplicationId id) {
        Optional<Integer> major = applicationPackage.deploymentSpec().majorVersion();
        if (major.isPresent())
            return controller.applications().lastCompatibleVersion(major.get())
                             .orElseThrow(() -> new IllegalArgumentException("major " + major.get() + " specified in deployment.xml, " +
                                                                             "but no version on this major was found"));

        // Prefer previous platform if possible.
        VersionStatus versionStatus = controller.readVersionStatus();
        VersionCompatibility compatibility = controller.applications().versionCompatibility(id);
        Optional<Version> target = lastRun.map(run -> run.versions().targetPlatform()).filter(versionStatus::isActive);
        if (target.isPresent() && compatibility.accept(target.get(), applicationPackage.compileVersion().orElse(target.get())))
            return target.get();

        // Otherwise, use newest, compatible version.
        for (VespaVersion platform : reversed(versionStatus.deployableVersions()))
            if (compatibility.accept(platform.versionNumber(), applicationPackage.compileVersion().orElse(platform.versionNumber())))
                return platform.versionNumber();

        throw new IllegalArgumentException("no suitable platform version found" +
                                           applicationPackage.compileVersion()
                                                             .map(version -> " for package compiled against " + version)
                                                             .orElse(""));
    }

    /** Aborts a run and waits for it complete. */
    private void abortAndWait(RunId id) {
        abort(id, "replaced by new deployment");
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
                curator.writeLastRun(modifications.apply(run));
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
