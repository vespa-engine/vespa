// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.component.Version;
import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.application.api.DeploymentSpec.Step;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.ApplicationController;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.BuildService;
import com.yahoo.vespa.hosted.controller.api.integration.BuildService.JobState;
import com.yahoo.vespa.hosted.controller.application.ApplicationList;
import com.yahoo.vespa.hosted.controller.application.ApplicationVersion;
import com.yahoo.vespa.hosted.controller.application.Change;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobError;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobReport;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType;
import com.yahoo.vespa.hosted.controller.application.JobStatus;
import com.yahoo.vespa.hosted.controller.application.JobStatus.JobRun;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static com.yahoo.config.provision.Environment.prod;
import static com.yahoo.config.provision.Environment.staging;
import static com.yahoo.config.provision.Environment.test;
import static com.yahoo.vespa.hosted.controller.api.integration.BuildService.BuildJob;
import static com.yahoo.vespa.hosted.controller.api.integration.BuildService.JobState.idle;
import static com.yahoo.vespa.hosted.controller.api.integration.BuildService.JobState.queued;
import static com.yahoo.vespa.hosted.controller.api.integration.BuildService.JobState.running;
import static com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType.component;
import static com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType.stagingTest;
import static com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType.systemTest;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Comparator.comparing;
import static java.util.Comparator.naturalOrder;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.partitioningBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

/**
 * Responsible for scheduling deployment jobs in a build system and keeping
 * {@link Application#change()} in sync with what is scheduled.
 *
 * This class is multi-thread safe.
 *
 * @author bratseth
 * @author mpolden
 * @author jvenstad
 */
public class DeploymentTrigger {

    private final static Logger log = Logger.getLogger(DeploymentTrigger.class.getName());

    private final Controller controller;
    private final Clock clock;
    private final DeploymentOrder order;
    private final BuildService buildService;

    public DeploymentTrigger(Controller controller, CuratorDb curator, BuildService buildService, Clock clock) {
        Objects.requireNonNull(controller, "controller cannot be null");
        Objects.requireNonNull(curator, "curator cannot be null");
        Objects.requireNonNull(clock, "clock cannot be null");
        this.controller = controller;
        this.clock = clock;
        this.order = new DeploymentOrder(controller::system);
        this.buildService = buildService;
    }

    public DeploymentOrder deploymentOrder() {
        return order;
    }

    /**
     * Records information when a job completes (successfully or not). This information is used when deciding what to
     * trigger next.
     */
    public void notifyOfCompletion(JobReport report) {
        log.log(LogLevel.INFO, String.format("Notified of %s for %s of %s (%d).",
                                             report.jobError().map(e -> e.toString() + " error")
                                                   .orElse("success"),
                                             report.jobType(),
                                             report.applicationId(),
                                             report.projectId()));
        if ( ! applications().get(report.applicationId()).isPresent()) {
            log.log(LogLevel.WARNING, "Ignoring completion of job of project '" + report.projectId() +
                                      "': Unknown application '" + report.applicationId() + "'");
            return;
        }

        applications().lockOrThrow(report.applicationId(), application -> {
            JobRun triggering;
            if (report.jobType() == component) {
                ApplicationVersion applicationVersion = ApplicationVersion.from(report.sourceRevision().get(), report.buildNumber());
                triggering = JobRun.triggering(controller.systemVersion(), applicationVersion, Optional
                        .empty(), Optional.empty(), "Application commit", clock.instant());
                if (report.success()) {
                    if (acceptNewApplicationVersion(application))
                        application = application.withChange(application.change().with(applicationVersion))
                                                 .withOutstandingChange(Change.empty());
                    else
                        application = application.withOutstandingChange(Change.of(applicationVersion));
                }
            } else {
                triggering = application.deploymentJobs().statusOf(report.jobType()).flatMap(JobStatus::lastTriggered)
                                        .orElseThrow(() -> new IllegalStateException("Notified of completion of " + report.jobType().jobName() + " for " +
                                                                                     report.applicationId() + ", but that has neither been triggered nor deployed"));
            }
            application = application.withJobCompletion(report.projectId(),
                                                        report.jobType(),
                                                        triggering.completion(report.buildNumber(), clock.instant()),
                                                        report.jobError());
            application = application.withChange(remainingChange(application));
            applications().store(application);
        });
    }

    /** Returns a map of jobs that are scheduled to be run, grouped by the job type */
    public Map<JobType, ? extends List<? extends BuildJob>> jobsToRun() {
        return computeReadyJobs().stream().collect(groupingBy(Job::jobType));
    }

    /**
     * Finds and triggers jobs that can and should run but are currently not, and returns the number of triggered jobs.
     *
     * Only one job is triggered each run for test jobs, since their environments have limited capacity.
     */
    public long triggerReadyJobs() {
        return computeReadyJobs().stream()
                                 .collect(partitioningBy(job -> job.jobType().isTest()))
                                 .entrySet().stream()
                                 .flatMap(entry -> (entry.getKey()
                                         // True for capacity constrained zones -- sort by priority and make a task for each job type.
                                         ? entry.getValue().stream()
                                                .sorted(comparing(Job::isRetry)
                                                                .thenComparing(Job::applicationUpgrade)
                                                                .reversed()
                                                                .thenComparing(Job::availableSince))
                                                .collect(groupingBy(Job::jobType))
                                         // False for production jobs -- keep step order and make a task for each application.
                                         : entry.getValue().stream()
                                                .collect(groupingBy(Job::applicationId)))
                                         .values().stream()
                                         .map(jobs -> (Supplier<Long>) jobs.stream()
                                                                           .filter(this::trigger)
                                                                           .limit(entry.getKey() ? 1 : Long.MAX_VALUE)::count))
                                 .parallel().map(Supplier::get).reduce(0L, Long::sum);
    }

    /**
     * Attempts to trigger the given job for the given application and returns the outcome.
     *
     * If the build service can not find the given job, or claims it is illegal to trigger it,
     * the project id is removed from the application owning the job, to prevent further trigger attemps.
     */
    public boolean trigger(Job job) {
        log.log(LogLevel.INFO, String.format("Triggering %s: %s", job, job.triggering));
        try {
            buildService.trigger(job);
            applications().lockOrThrow(job.applicationId(), application ->
                    applications().store(application.withJobTriggering(job.jobType, job.triggering)));
            return true;
        }
        catch (RuntimeException e) {
            log.log(LogLevel.WARNING, "Exception triggering " + job + ": " + e);
            if (e instanceof NoSuchElementException || e instanceof IllegalArgumentException)
                applications().lockOrThrow(job.applicationId(), application ->
                        applications().store(application.withProjectId(OptionalLong.empty())));
            return false;
        }
    }

    /** Force triggering of a job for given application. */
    public List<JobType> forceTrigger(ApplicationId applicationId, JobType jobType, String user) {
        Application application = applications().require(applicationId);
        if (jobType == component) {
            buildService.trigger(BuildJob.of(applicationId, application.deploymentJobs().projectId().getAsLong(), jobType.jobName()));
            return singletonList(component);
        }
        Versions versions = versions(application, application.change(), deploymentFor(application, jobType));
        String reason = "Job triggered manually by " + user;
        return (jobType.isProduction() && ! isTested(application, versions)
                ? testJobs(application, versions, reason, clock.instant()).stream()
                : Stream.of(deploymentJob(application, versions, application.change(), jobType, reason, clock.instant())))
                .peek(this::trigger)
                .map(Job::jobType).collect(toList());
    }

    /**
     * Triggers a change of this application
     *
     * @param applicationId the application to trigger
     * @throws IllegalArgumentException if this application already has an ongoing change
     */
    public void triggerChange(ApplicationId applicationId, Change change) {
        applications().lockOrThrow(applicationId, application -> {
            if (application.change().isPresent() && ! application.deploymentJobs().hasFailures())
                throw new IllegalArgumentException("Could not start " + change + " on " + application + ": " +
                                                   application.change() + " is already in progress");
            application = application.withChange(change);
            if (change.application().isPresent())
                application = application.withOutstandingChange(Change.empty());

            applications().store(application);
        });
    }

    /** Cancels a platform upgrade of the given application, and an application upgrade as well if {@code keepApplicationChange}. */
    public void cancelChange(ApplicationId applicationId, boolean keepApplicationChange) {
        applications().lockOrThrow(applicationId, application -> {
            applications().store(application.withChange(application.change().application()
                                                                   .filter(__ -> keepApplicationChange)
                                                                   .map(Change::of)
                                                                   .orElse(Change.empty())));
        });
    }

    // ---------- Conveniences ----------

    private ApplicationController applications() {
        return controller.applications();
    }

    private Optional<JobRun> successOn(Application application, JobType jobType, Versions versions) {
        return application.deploymentJobs().statusOf(jobType).flatMap(JobStatus::lastSuccess)
                          .filter(run -> targetsMatch(versions, run));
    }

    private Optional<Deployment> deploymentFor(Application application, JobType jobType) {
        return Optional.ofNullable(application.deployments().get(jobType.zone(controller.system()).get()));
    }

    private static <T extends Comparable<T>> Optional<T> max(Optional<T> o1, Optional<T> o2) {
        return ! o1.isPresent() ? o2 : ! o2.isPresent() ? o1 : o1.get().compareTo(o2.get()) >= 0 ? o1 : o2;
    }

    // ---------- Ready job computation ----------

    /** Returns the set of all jobs which have changes to propagate from the upstream steps. */
    private List<Job> computeReadyJobs() {
        return ApplicationList.from(applications().asList())
                              .notPullRequest()
                              .withProjectId()
                              .deploying()
                              .idList().stream()
                              .map(this::computeReadyJobs)
                              .flatMap(Collection::stream)
                              .collect(toList());
    }

    /**
     * Finds the next step to trigger for the given application, if any, and returns these as a list.
     */
    private List<Job> computeReadyJobs(ApplicationId id) {
        List<Job> jobs = new ArrayList<>();
        applications().get(id).ifPresent(application -> {
            List<Step> productionSteps = application.deploymentSpec().steps().stream()
                                                    .filter(step -> step.deploysTo(prod) || step.zones().isEmpty())
                                                    .collect(toList());

            Change change = application.changeAt(clock.instant());
            Optional<Instant> completedAt = max(application.deploymentJobs().statusOf(systemTest)
                                                        .<Instant>flatMap(job -> job.lastSuccess().map(JobRun::at)),
                                                application.deploymentJobs().statusOf(stagingTest)
                                                        .<Instant>flatMap(job -> job.lastSuccess().map(JobRun::at)));
            String reason = "New change available";
            List<Job> testJobs = null; // null means "uninitialised", while empty means "don't run any jobs".

            if (change.isPresent())
                for (Step step : productionSteps) {
                    Set<JobType> stepJobs = step.zones().stream().map(order::toJob).collect(toSet());
                    Map<Instant, List<JobType>> jobsByCompletion = stepJobs.stream().collect(groupingBy(job -> completedAt(change, application, job).orElse(null)));
                    if (jobsByCompletion.containsKey(null)) { // Step incomplete because some jobs remain, trigger those if the previous step was done, or required test steps.
                        for (JobType job : jobsByCompletion.get(null)) {
                            Versions versions = versions(application, change, deploymentFor(application, job));
                            if (isTested(application, versions)) {
                                if (completedAt.isPresent()
                                    && jobStateContains(application, job, idle)
                                    && stepJobs.containsAll(runningProductionJobs(application)))
                                    jobs.add(deploymentJob(application, versions, change, job, reason, completedAt.get()));
                                if ( ! alreadyTriggered(application, versions))
                                    testJobs = emptyList();
                            }
                            else if (testJobs == null) {
                                testJobs = testJobs(application, versions, String.format("Testing deployment for %s (%s)", job.jobName(), versions.toString()),
                                                    completedAt.orElse(clock.instant()));
                            }
                        }
                        completedAt = Optional.empty();
                    }
                    else { // All jobs are complete -- find the time of completion of this step.
                        if (stepJobs.isEmpty()) { // No jobs means this is delay step.
                            Duration delay = ((DeploymentSpec.Delay) step).duration();
                            completedAt = completedAt.map(at -> at.plus(delay)).filter(at -> ! at.isAfter(clock.instant()));
                            reason += " after a delay of " + delay;
                        }
                        else {
                            completedAt = jobsByCompletion.keySet().stream().max(naturalOrder());
                            reason = "Available change in " + stepJobs.stream().map(JobType::jobName).collect(joining(", "));
                        }
                    }
                }
            if (testJobs == null)
                testJobs = testJobs(application, versions(application, application.change(), Optional
                        .empty()), "Testing last changes outside prod", clock.instant());
            jobs.addAll(testJobs);
        });
        return jobs;
    }

    // ---------- Job state helpers ----------

    private List<JobType> runningProductionJobs(Application application) {
        return application.deploymentJobs().jobStatus().keySet().parallelStream()
                          .filter(JobType::isProduction)
                          .filter(job -> isRunning(application, job))
                          .collect(toList());
    }

    /** Returns whether the given job is currently running; false if completed since last triggered, asking the build service otherwise. */
    private boolean isRunning(Application application, JobType jobType) {
        return ! application.deploymentJobs().statusOf(jobType)
                               .flatMap(job -> job.lastCompleted().map(run -> run.at().isAfter(job.lastTriggered().get().at())))
                               .orElse(false)
               && jobStateContains(application, jobType, running, queued);
    }

    private boolean jobStateContains(Application application, JobType jobType, JobState... states) {
        return Arrays.asList(states).contains(buildService.stateOf(BuildJob.of(application.id(),
                                                                               application.deploymentJobs().projectId().getAsLong(),
                                                                               jobType.jobName())));
    }

    // ---------- Completion logic ----------

    /**
     * Returns the instant when the given change is complete for the given application for the given job.
     *
     * Any job is complete if the given change is already successful on that job.
     * A production job is also considered complete if its current change is strictly dominated by what
     * is already deployed in its zone, i.e., no parts of the change are upgrades, and the full current
     * change for the application downgrades the deployment, which is an acknowledgement that the deployed
     * version is broken somehow, such that the job may be locked in failure until a new version is released.
     */
    private Optional<Instant> completedAt(Change change, Application application, JobType jobType) {
        Versions versions = versions(application, change, deploymentFor(application, jobType));
        Optional<JobRun> lastSuccess = successOn(application, jobType, versions);
        if (lastSuccess.isPresent() || ! jobType.isProduction())
            return lastSuccess.map(JobRun::at);

        return deploymentFor(application, jobType)
                .filter(deployment -> !isUpgrade(change, deployment) && isDowngrade(application.change(), deployment))
                .map(Deployment::at);
    }

    private static boolean isUpgrade(Change change, Deployment deployment) {
        return change.upgrades(deployment.version()) || change.upgrades(deployment.applicationVersion());
    }

    private static boolean isDowngrade(Change change, Deployment deployment) {
        return change.downgrades(deployment.version()) || change.downgrades(deployment.applicationVersion());
    }

    private boolean isTested(Application application, Versions versions) {
        return testedAt(application, versions).isPresent() || alreadyTriggered(application, versions);
    }

    private Optional<Instant> testedAt(Application application, Versions versions) {
        Optional<JobRun> testRun = successOn(application, systemTest, versions);
        Optional<JobRun> stagingRun = successOn(application, stagingTest, versions)
                .filter(run -> sourcesMatchIfPresent(versions, run));
        return max(testRun.map(JobRun::at), stagingRun.map(JobRun::at))
                .filter(__ -> testRun.isPresent() && stagingRun.isPresent());
    }

    private boolean alreadyTriggered(Application application, Versions versions) {
        return application.deploymentJobs().jobStatus().values().stream()
                          .filter(job -> job.type().isProduction())
                          .anyMatch(job -> job.lastTriggered()
                                              .filter(run -> targetsMatch(versions, run))
                                              .filter(run -> sourcesMatchIfPresent(versions, run))
                                              .isPresent());
    }

    /** If the given state's sources are present and differ from its targets, returns whether they are equal to those
     * of the given job run. */
    private static boolean sourcesMatchIfPresent(Versions versions, JobRun jobRun) {
        return   (   ! versions.sourcePlatform.filter(version -> ! version.equals(versions.targetPlatform)).isPresent()
                     ||   versions.sourcePlatform.equals(jobRun.sourcePlatform()))
                 && (   ! versions.sourceApplication.filter(version -> ! version.equals(versions.targetApplication)).isPresent()
                        ||   versions.sourceApplication.equals(jobRun.sourceApplication()));
    }

    private static boolean targetsMatch(Versions versions, JobRun jobRun) {
        return versions.targetPlatform.equals(jobRun.platform()) && versions.targetApplication.equals(jobRun.application());
    }

    // ---------- Change management o_O ----------

    private boolean acceptNewApplicationVersion(Application application) {
        if (application.change().application().isPresent()) return true; // More application changes are ok.
        if (application.deploymentJobs().hasFailures()) return true; // Allow changes to fix upgrade problems.
        return ! application.changeAt(clock.instant()).platform().isPresent();
    }

    private Change remainingChange(Application application) {
        List<JobType> jobs = (application.deploymentSpec().steps().isEmpty()
                ? singletonList(new DeploymentSpec.DeclaredZone(test))
                : application.deploymentSpec().steps()).stream()
                                                       .flatMap(step -> step.zones().stream())
                                                       .map(order::toJob)
                                                       .collect(toList());

        boolean platformComplete = application.change().platform().map(Change::of)
                                              .map(change -> jobs.stream().allMatch(job -> completedAt(change, application, job).isPresent()))
                                              .orElse(false);

        boolean applicationComplete = application.change().application().map(Change::of)
                                                 .map(change -> jobs.stream().allMatch(job -> completedAt(change, application, job).isPresent()))
                                                 .orElse(false);

        Change change = application.change();
        if (platformComplete) change = change.withoutPlatform();
        if (applicationComplete) change = change.withoutApplication();
        return change;
    }

    // ---------- Version and job helpers ----------

    /**
     * Returns the list of test jobs that should run now, and that need to succeed on the given versions for it to be considered tested.
     */
    private List<Job> testJobs(Application application, Versions versions, String reason, Instant availableSince) {
        List<Step> steps = application.deploymentSpec().steps();
        if (steps.isEmpty()) steps = singletonList(new DeploymentSpec.DeclaredZone(test));
        List<Job> jobs = new ArrayList<>();
        for (Step step : steps.stream().filter(step -> step.deploysTo(test) || step.deploysTo(staging)).collect(toList())) {
            for (JobType jobType : step.zones().stream().map(order::toJob).collect(toList())) {
                Optional<JobRun> completion = successOn(application, jobType, versions)
                        .filter(run -> jobType != stagingTest || sourcesMatchIfPresent(versions, run));
                if (! completion.isPresent() && jobStateContains(application, jobType, idle))
                    jobs.add(deploymentJob(application, versions, application.change(), jobType, reason, availableSince));
            }
        }
        return jobs;
    }

    private Job deploymentJob(Application application, Versions versions, Change change, JobType jobType, String reason, Instant availableSince) {
        boolean isRetry = application.deploymentJobs().statusOf(jobType).flatMap(JobStatus::jobError)
                                     .filter(JobError.outOfCapacity::equals).isPresent();
        if (isRetry) reason += "; retrying on out of capacity";

        JobRun triggering = JobRun.triggering(versions.targetPlatform, versions.targetApplication, versions.sourcePlatform, versions.sourceApplication, reason, clock.instant());
        return new Job(application, triggering, jobType, availableSince, isRetry, change.application().isPresent());
    }

    private Versions versions(Application application, Change change, Optional<Deployment> deployment) {
        return new Versions(targetPlatform(application, change, deployment),
                            targetApplication(application, change, deployment),
                            deployment.map(Deployment::version),
                            deployment.map(Deployment::applicationVersion));
    }

    private Version targetPlatform(Application application, Change change, Optional<Deployment> deployment) {
        return max(deployment.map(Deployment::version), change.platform())
                .orElse(application.oldestDeployedPlatform()
                                   .orElse(controller.systemVersion()));
    }

    private ApplicationVersion targetApplication(Application application, Change change, Optional<Deployment> deployment) {
        return max(deployment.map(Deployment::applicationVersion), change.application())
                .orElse(application.oldestDeployedApplication()
                                   .orElse(application.deploymentJobs().jobStatus().get(component).lastSuccess().get().application()));
    }

    // ---------- Data containers ----------


    private static class Job extends BuildJob {

        private final JobType jobType;
        private final JobRun triggering;
        private final Instant availableSince;
        private final boolean isRetry;
        private final boolean isApplicationUpgrade;

        private Job(Application application, JobRun triggering, JobType jobType, Instant availableSince,
                    boolean isRetry, boolean isApplicationUpgrade) {
            super(application.id(), application.deploymentJobs().projectId().getAsLong(), jobType.jobName());
            this.jobType = jobType;
            this.triggering = triggering;
            this.availableSince = availableSince;
            this.isRetry = isRetry;
            this.isApplicationUpgrade = isApplicationUpgrade;
        }

        JobType jobType() { return jobType; }
        Instant availableSince() { return availableSince; } // TODO jvenstad: This is 95% broken now. Change.at() can restore it.
        boolean isRetry() { return isRetry; }
        boolean applicationUpgrade() { return isApplicationUpgrade; }

    }


    private static class Versions {

        private final Version targetPlatform;
        private final ApplicationVersion targetApplication;
        private final Optional<Version> sourcePlatform;
        private final Optional<ApplicationVersion> sourceApplication;

        private Versions(Version targetPlatform, ApplicationVersion targetApplication, Optional<Version> sourcePlatform,
                         Optional<ApplicationVersion> sourceApplication) {
            this.targetPlatform = targetPlatform;
            this.targetApplication = targetApplication;
            this.sourcePlatform = sourcePlatform;
            this.sourceApplication = sourceApplication;
        }

        @Override
        public String toString() {
            return String.format("platform %s%s, application %s%s",
                                 sourcePlatform.filter(src -> !src.equals(targetPlatform))
                                               .map(src -> src + " -> ")
                                               .orElse(""),
                                 targetPlatform,
                                 sourceApplication.filter(src -> !src.equals(targetApplication))
                                                  .map(ApplicationVersion::id)
                                                  .map(src -> src + " -> ")
                                                  .orElse(""),
                                 targetApplication.id());
        }
    }

}

