// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.deployment;

import com.google.common.collect.ImmutableMap;
import com.yahoo.component.Version;
import com.yahoo.config.application.api.DeploymentInstanceSpec;
import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.application.api.DeploymentSpec.DeclaredTest;
import com.yahoo.config.application.api.DeploymentSpec.DeclaredZone;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.Instance;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.application.Change;
import com.yahoo.vespa.hosted.controller.application.Deployment;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.yahoo.config.provision.Environment.prod;
import static com.yahoo.config.provision.Environment.staging;
import static com.yahoo.config.provision.Environment.test;
import static com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType.stagingTest;
import static com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType.systemTest;
import static java.util.Comparator.comparing;
import static java.util.Comparator.naturalOrder;
import static java.util.Objects.requireNonNull;
import static java.util.function.BinaryOperator.maxBy;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toUnmodifiableList;

/**
 * Status of the deployment jobs of an {@link Application}.
 *
 * @author jonmv
 */
public class DeploymentStatus {

    public static List<JobId> jobsFor(Application application, SystemName system) {
        if (DeploymentSpec.empty.equals(application.deploymentSpec()))
            return List.of();

        return application.deploymentSpec().instances().stream()
                          .flatMap(spec -> Stream.concat(Stream.of(systemTest, stagingTest),
                                                         flatten(spec).filter(step -> step.concerns(prod))
                                                                      .map(step -> {
                                                                          if (step instanceof DeclaredZone)
                                                                              return JobType.from(system, prod, ((DeclaredZone) step).region().get());
                                                                          return JobType.testFrom(system, ((DeclaredTest) step).region());
                                                                      })
                                                                      .flatMap(Optional::stream))
                                                 .map(type -> new JobId(application.id().instance(spec.name()), type)))
                          .collect(toUnmodifiableList());
    }

    private static Stream<DeploymentSpec.Step> flatten(DeploymentSpec.Step step) {
        return step instanceof DeploymentSpec.Steps ? step.steps().stream().flatMap(DeploymentStatus::flatten) : Stream.of(step);
    }

    private static <T> List<T> union(List<T> first, List<T> second) {
        return Stream.concat(first.stream(), second.stream()).distinct().collect(toUnmodifiableList());
    }

    private final Application application;
    private final JobList allJobs;
    private final SystemName system;
    private final Version systemVersion;
    private final Instant now;
    private final Map<JobId, StepStatus> jobSteps;
    private final List<StepStatus> allSteps;

    public DeploymentStatus(Application application, Map<JobId, JobStatus> allJobs, SystemName system,
                            Version systemVersion, Instant now) {
        this.application = requireNonNull(application);
        this.allJobs = JobList.from(allJobs.values());
        this.system = requireNonNull(system);
        this.systemVersion = requireNonNull(systemVersion);
        this.now = requireNonNull(now);
        List<StepStatus> allSteps = new ArrayList<>();
        this.jobSteps = jobDependencies(application.deploymentSpec(), allSteps);
        this.allSteps = List.copyOf(allSteps);
    }

    /** The application this deployment status concerns. */
    public Application application() {
        return application;
    }

    /** A filterable list of the status of all jobs for this application. */
    public JobList jobs() {
        return allJobs;
    }

    /** Whether any jobs of this application are failing with other errors than lack of capacity in a test zone. */
    public boolean hasFailures() {
        return ! allJobs.failing()
                        .not().withStatus(RunStatus.outOfCapacity)
                        .isEmpty();
    }

    /** All job statuses, by job type, for the given instance. */
    public Map<JobType, JobStatus> instanceJobs(InstanceName instance) {
        return allJobs.asList().stream()
                      .filter(job -> job.id().application().equals(application.id().instance(instance)))
                      .collect(Collectors.toUnmodifiableMap(job -> job.id().type(),
                                                            Function.identity()));
    }

    /** Filterable job status lists for each instance of this application. */
    public Map<ApplicationId, JobList> instanceJobs() {
        return allJobs.groupingBy(job -> job.id().application());
    }

    /**
     * The set of jobs that need to run for the changes of each instance of the application to be considered complete,
     * and any test jobs for any oustanding change, which will likely be needed to lated deploy this change.
     */
    public Map<JobId, List<Versions>> jobsToRun() {
        Map<InstanceName, Change> changes = new LinkedHashMap<>();
        for (InstanceName instance : application.deploymentSpec().instanceNames())
            changes.put(instance, application.require(instance).change());
        Map<JobId, List<Versions>> jobs = jobsToRun(changes);

        // Add test jobs for any outstanding change.
        for (InstanceName instance : application.deploymentSpec().instanceNames())
            changes.put(instance, outstandingChange(instance).onTopOf(application.require(instance).change()));
        var testJobs = jobsToRun(changes, true).entrySet().stream()
                                               .filter(entry -> ! entry.getKey().type().isProduction());

        return Stream.concat(jobs.entrySet().stream(), testJobs)
                     .collect(collectingAndThen(toMap(Map.Entry::getKey,
                                                      Map.Entry::getValue,
                                                      DeploymentStatus::union,
                                                      LinkedHashMap::new),
                                                ImmutableMap::copyOf));
    }

    private Map<JobId, List<Versions>> jobsToRun(Map<InstanceName, Change> changes, boolean eagerTests) {
        Map<JobId, Versions> productionJobs = new LinkedHashMap<>();
        changes.forEach((instance, change) -> productionJobs.putAll(productionJobs(instance, change, eagerTests)));
        Map<JobId, List<Versions>> testJobs = testJobs(productionJobs);
        Map<JobId, List<Versions>> jobs = new LinkedHashMap<>(testJobs);
        productionJobs.forEach((job, versions) -> jobs.put(job, List.of(versions)));
        // Add runs for idle, declared test jobs if they have no successes on their instance's change's versions.
        jobSteps.forEach((job, step) -> {
            if ( ! step.isDeclared() || jobs.containsKey(job))
                return;

            Change change = changes.get(job.application().instance());
            if (change == null || ! change.hasTargets())
                return;

            Optional<JobId> firstProductionJobWithDeployment = jobSteps.keySet().stream()
                                                                       .filter(jobId -> jobId.type().isProduction() && jobId.type().isDeployment())
                                                                       .filter(jobId -> deploymentFor(jobId).isPresent())
                                                                       .findFirst();

            Versions versions = Versions.from(change, application, firstProductionJobWithDeployment.flatMap(this::deploymentFor), systemVersion);
            if (step.completedAt(change, firstProductionJobWithDeployment).isEmpty())
                jobs.merge(job, List.of(versions), DeploymentStatus::union);
        });
        return ImmutableMap.copyOf(jobs);
    }

    /** The set of jobs that need to run for the given changes to be considered complete. */
    public Map<JobId, List<Versions>> jobsToRun(Map<InstanceName, Change> changes) {
        return jobsToRun(changes, false);
    }

    /** The step status for all steps in the deployment spec of this, which are jobs, in the same order as in the deployment spec. */
    public Map<JobId, StepStatus> jobSteps() { return jobSteps; }

    public Map<InstanceName, StepStatus> instanceSteps() {
        ImmutableMap.Builder<InstanceName, StepStatus> instances = ImmutableMap.builder();
        for (StepStatus status : allSteps)
            if (status instanceof InstanceStatus)
                instances.put(status.instance(), status);
        return instances.build();
    }

    /** The step status for all relevant steps in the deployment spec of this, in the same order as in the deployment spec. */
    public List<StepStatus> allSteps() {
        if (allSteps.isEmpty())
            return List.of();

        List<JobId> firstTestJobs = List.of(firstDeclaredOrElseImplicitTest(systemTest),
                                            firstDeclaredOrElseImplicitTest(stagingTest));
        return allSteps.stream()
                       .filter(step -> step.isDeclared() || firstTestJobs.contains(step.job().orElseThrow()))
                       .collect(toUnmodifiableList());
    }

    public Optional<Deployment> deploymentFor(JobId job) {
        return Optional.ofNullable(application.require(job.application().instance())
                                              .deployments().get(job.type().zone(system)));
    }

    /**
     * The change of this application's latest submission, if this upgrades any of its production deployments,
     * and has not yet started rolling out, due to some other change or a block window being present at the time of submission.
     */
    public Change outstandingChange(InstanceName instance) {
        return application.latestVersion().map(Change::of)
                          .filter(change -> application.require(instance).change().application().map(change::upgrades).orElse(true))
                          .filter(change -> ! jobsToRun(Map.of(instance, change)).isEmpty())
                          .orElse(Change.empty());
    }

    /**
     * True if the job has already been triggered on the given versions, or if all test types (systemTest, stagingTest),
     * restricted to the job's instance if declared in that instance, have successful runs on the given versions.
     */
    public boolean isTested(JobId job, Change change) {
        Versions versions = Versions.from(change, application, deploymentFor(job), systemVersion);
        return    allJobs.triggeredOn(versions).get(job).isPresent()
               || Stream.of(systemTest, stagingTest)
                        .noneMatch(testType -> declaredTest(job.application(), testType).map(__ -> allJobs.instance(job.application().instance()))
                                                                                        .orElse(allJobs)
                                                                                        .type(testType)
                                                                                        .successOn(versions).isEmpty());
    }

    private Map<JobId, Versions> productionJobs(InstanceName instance, Change change, boolean assumeUpgradesSucceed) {
        ImmutableMap.Builder<JobId, Versions> jobs = ImmutableMap.builder();
        jobSteps.forEach((job, step) -> {
            // When computing eager test jobs for outstanding changes, assume current upgrade completes successfully.
            Optional<Deployment> deployment = deploymentFor(job)
                    .map(existing -> assumeUpgradesSucceed ? new Deployment(existing.zone(),
                                                                            existing.applicationVersion(),
                                                                            change.platform().orElse(existing.version()),
                                                                            existing.at(),
                                                                            existing.metrics(),
                                                                            existing.activity(),
                                                                            existing.quota())
                                                           : existing);
            if (   job.application().instance().equals(instance)
                && job.type().isProduction()
                && step.completedAt(change).isEmpty())
                jobs.put(job, Versions.from(change, application, deployment, systemVersion));
        });
        return jobs.build();
    }

    /** The production jobs that need to run to complete roll-out of the given change to production. */
    public Map<JobId, Versions> productionJobs(InstanceName instance, Change change) {
        return productionJobs(instance, change, false);
    }

    /** The test jobs that need to run prior to the given production deployment jobs. */
    public Map<JobId, List<Versions>> testJobs(Map<JobId, Versions> jobs) {
        Map<JobId, List<Versions>> testJobs = new LinkedHashMap<>();
        for (JobType testType : List.of(systemTest, stagingTest)) {
            jobs.forEach((job, versions) -> {
                if (job.type().isProduction() && job.type().isDeployment()) {
                    declaredTest(job.application(), testType).ifPresent(testJob -> {
                        if (allJobs.successOn(versions).get(testJob).isEmpty())
                            testJobs.merge(testJob, List.of(versions), DeploymentStatus::union);
                    });
                }
            });
            jobs.forEach((job, versions) -> {
                if (   job.type().isProduction() && job.type().isDeployment()
                    && allJobs.successOn(versions).type(testType).isEmpty()
                    && testJobs.keySet().stream()
                               .noneMatch(test ->    test.type() == testType
                                                  && testJobs.get(test).contains(versions)))
                    testJobs.merge(firstDeclaredOrElseImplicitTest(testType), List.of(versions), DeploymentStatus::union);
            });
        }
        return ImmutableMap.copyOf(testJobs);
    }

    private JobId firstDeclaredOrElseImplicitTest(JobType testJob) {
        return application.deploymentSpec().instanceNames().stream()
                          .map(name -> new JobId(application.id().instance(name), testJob))
                          .min(comparing(id -> ! jobSteps.get(id).isDeclared())).orElseThrow();
    }

    /** JobId of any declared test of the given type, for the given instance. */
    private Optional<JobId> declaredTest(ApplicationId instanceId, JobType testJob) {
        JobId jobId = new JobId(instanceId, testJob);
        return jobSteps.get(jobId).isDeclared() ? Optional.of(jobId) : Optional.empty();
    }

    /** A DAG of the dependencies between the primitive steps in the spec, with iteration order equal to declaration order. */
    private Map<JobId, StepStatus> jobDependencies(DeploymentSpec spec, List<StepStatus> allSteps) {
        if (DeploymentSpec.empty.equals(spec))
            return Map.of();

        Map<JobId, StepStatus> dependencies = new LinkedHashMap<>();
        List<StepStatus> previous = List.of();
        for (DeploymentSpec.Step step : spec.steps())
            previous = fillStep(dependencies, allSteps, step, previous, null);

        return ImmutableMap.copyOf(dependencies);
    }

    /** Adds the primitive steps contained in the given step, which depend on the given previous primitives, to the dependency graph. */
    private List<StepStatus> fillStep(Map<JobId, StepStatus> dependencies, List<StepStatus> allSteps,
                                      DeploymentSpec.Step step, List<StepStatus> previous, InstanceName instance) {
        if (step.steps().isEmpty()) {
            if (instance == null)
                return previous; // Ignore test and staging outside all instances.

            if ( ! step.delay().isZero()) {
                StepStatus stepStatus = new DelayStatus((DeploymentSpec.Delay) step, previous, instance);
                allSteps.add(stepStatus);
                return List.of(stepStatus);
            }

            JobType jobType;
            StepStatus stepStatus;
            if (step.concerns(test) || step.concerns(staging)) {
                jobType = JobType.from(system, ((DeclaredZone) step).environment(), null)
                                 .orElseThrow(() -> new IllegalStateException(application + " specifies " + step + ", but this has no job in " + system));
                stepStatus = JobStepStatus.ofTestDeployment((DeclaredZone) step, List.of(), this, instance, jobType, true);
                previous = new ArrayList<>(previous);
                previous.add(stepStatus);
            }
            else if (step.isTest()) {
                jobType = JobType.testFrom(system, ((DeclaredTest) step).region())
                                 .orElseThrow(() -> new IllegalStateException(application + " specifies " + step + ", but this has no job in " + system));
                JobType preType = JobType.from(system, prod, ((DeclaredTest) step).region())
                                         .orElseThrow(() -> new IllegalStateException(application + " specifies " + step + ", but this has no job in " + system));
                stepStatus = JobStepStatus.ofProductionTest((DeclaredTest) step, previous, this, instance, jobType, preType);
                previous = List.of(stepStatus);
            }
            else if (step.concerns(prod)) {
                jobType = JobType.from(system, ((DeclaredZone) step).environment(), ((DeclaredZone) step).region().get())
                                 .orElseThrow(() -> new IllegalStateException(application + " specifies " + step + ", but this has no job in " + system));
                stepStatus = JobStepStatus.ofProductionDeployment((DeclaredZone) step, previous, this, instance, jobType);
                previous = List.of(stepStatus);
            }
            else return previous; // Empty container steps end up here, and are simply ignored.
            JobId jobId = new JobId(application.id().instance(instance), jobType);
            allSteps.removeIf(existing -> existing.job().equals(Optional.of(jobId))); // Replace implicit tests with explicit ones.
            allSteps.add(stepStatus);
            dependencies.put(jobId, stepStatus);
            return previous;
        }

        if (step instanceof DeploymentInstanceSpec) {
            DeploymentInstanceSpec spec = ((DeploymentInstanceSpec) step);
            StepStatus instanceStatus = new InstanceStatus(spec, previous, now, application.require(spec.name()), this);
            instance = spec.name();
            allSteps.add(instanceStatus);
            previous = List.of(instanceStatus);
            for (JobType test : List.of(systemTest, stagingTest)) {
                JobId job = new JobId(application.id().instance(instance), test);
                if ( ! dependencies.containsKey(job)) {
                    var testStatus = JobStepStatus.ofTestDeployment(new DeclaredZone(test.environment()), List.of(),
                                                                    this, job.application().instance(), test, false);
                    dependencies.put(job, testStatus);
                    allSteps.add(testStatus);
                }
            }
        }

        if (step.isOrdered()) {
            for (DeploymentSpec.Step nested : step.steps())
                previous = fillStep(dependencies, allSteps, nested, previous, instance);

            return previous;
        }

        List<StepStatus> parallel = new ArrayList<>();
        for (DeploymentSpec.Step nested : step.steps())
            parallel.addAll(fillStep(dependencies, allSteps, nested, previous, instance));

        return List.copyOf(parallel);
    }


    public enum StepType {

        /** An instance — completion marks a change as ready for the jobs contained in it. */
        instance,

        /** A timed delay. */
        delay,

        /** A system, staging or production test. */
        test,

        /** A production deployment. */
        deployment,
    }

    /**
     * Used to represent all steps — explicit and implicit — that may run in order to complete deployment of a change.
     *
     * Each node contains a step describing the node,
     * a list of steps which need to be complete before the step may start,
     * a list of jobs from which completion of the step is computed, and
     * optionally, an instance name used to identify a job type for the step,
     *
     * The completion criterion for each type of step is implemented in subclasses of this.
     */
    public static abstract class StepStatus {

        private final StepType type;
        private final DeploymentSpec.Step step;
        private final List<StepStatus> dependencies;
        private final InstanceName instance;

        private StepStatus(StepType type, DeploymentSpec.Step step, List<StepStatus> dependencies, InstanceName instance) {
            this.type = requireNonNull(type);
            this.step = requireNonNull(step);
            this.dependencies = List.copyOf(dependencies);
            this.instance = instance;
        }

        /** The type of step this is. */
        public final StepType type() { return type; }

        /** The step defining this. */
        public final DeploymentSpec.Step step() { return step; }

        /** The list of steps that need to be complete before this may start. */
        public final List<StepStatus> dependencies() { return dependencies; }

        /** The instance of this. */
        public final InstanceName instance() { return instance; }

        /** The id of the job this corresponds to, if any. */
        public Optional<JobId> job() { return Optional.empty(); }

        /** The time at which this is, or was, complete on the given change and / or versions. */
        public Optional<Instant> completedAt(Change change) { return completedAt(change, Optional.empty()); }

        /** The time at which this is, or was, complete on the given change and / or versions. */
        abstract Optional<Instant> completedAt(Change change, Optional<JobId> dependent);

        /** The time at which this step is ready to run the specified change and / or versions. */
        public Optional<Instant> readyAt(Change change) { return readyAt(change, Optional.empty()); }

        /** The time at which this step is ready to run the specified change and / or versions. */
        Optional<Instant> readyAt(Change change, Optional<JobId> dependent) {
            return dependenciesCompletedAt(change, dependent)
                    .map(ready -> Stream.of(blockedUntil(change),
                                            pausedUntil(),
                                            coolingDownUntil(change))
                                        .flatMap(Optional::stream)
                                        .reduce(ready, maxBy(naturalOrder())));
        }

        /** The time at which all dependencies completed on the given change and / or versions. */
        Optional<Instant> dependenciesCompletedAt(Change change, Optional<JobId> dependent) {
            return dependencies.stream().allMatch(step -> step.completedAt(change, dependent).isPresent())
                   ? dependencies.stream().map(step -> step.completedAt(change, dependent).get())
                                 .max(naturalOrder())
                                 .or(() -> Optional.of(Instant.EPOCH))
                   : Optional.empty();
        }

        /** The time until which this step is blocked by a change blocker. */
        public Optional<Instant> blockedUntil(Change change) { return Optional.empty(); }

        /** The time until which this step is paused by user intervention. */
        public Optional<Instant> pausedUntil() { return Optional.empty(); }

        /** The time until which this step is cooling down, due to consecutive failures. */
        public Optional<Instant> coolingDownUntil(Change change) { return Optional.empty(); }

        /** Whether this step is declared in the deployment spec, or is an implicit step. */
        public boolean isDeclared() { return true; }

    }


    private static class DelayStatus extends StepStatus {

        private DelayStatus(DeploymentSpec.Delay step, List<StepStatus> dependencies, InstanceName instance) {
            super(StepType.delay, step, dependencies, instance);
        }

        @Override
        public Optional<Instant> completedAt(Change change, Optional<JobId> dependent) {
            return readyAt(change, dependent).map(completion -> completion.plus(step().delay()));
        }

    }


    private static class InstanceStatus extends StepStatus {

        private final DeploymentInstanceSpec spec;
        private final Instant now;
        private final Instance instance;
        private final DeploymentStatus status;

        private InstanceStatus(DeploymentInstanceSpec spec, List<StepStatus> dependencies, Instant now,
                               Instance instance, DeploymentStatus status) {
            super(StepType.instance, spec, dependencies, spec.name());
            this.spec = spec;
            this.now = now;
            this.instance = instance;
            this.status = status;
        }

        /**
         * Time of completion of its dependencies, if all parts of the given change are contained in the change
         * for this instance, or if no more jobs should run for this instance for the given change.
         */
        @Override
        public Optional<Instant> completedAt(Change change, Optional<JobId> dependent) {
            return    (   (change.platform().isEmpty() || change.platform().equals(instance.change().platform()))
                       && (change.application().isEmpty() || change.application().equals(instance.change().application()))
                   || status.jobsToRun(Map.of(instance.name(), change)).isEmpty())
                      ? dependenciesCompletedAt(change, dependent)
                      : Optional.empty();
        }

        @Override
        public Optional<Instant> blockedUntil(Change change) {
            for (Instant current = now; now.plus(Duration.ofDays(7)).isAfter(current); ) {
                boolean blocked = false;
                for (DeploymentSpec.ChangeBlocker blocker : spec.changeBlocker()) {
                    while (   blocker.window().includes(current)
                           && now.plus(Duration.ofDays(7)).isAfter(current)
                           && (   change.platform().isPresent() && blocker.blocksVersions()
                               || change.application().isPresent() && blocker.blocksRevisions())) {
                        blocked = true;
                        current = current.plus(Duration.ofHours(1)).truncatedTo(ChronoUnit.HOURS);
                    }
                }
                if ( ! blocked)
                    return current == now ? Optional.empty() : Optional.of(current);
            }
            return Optional.of(now.plusSeconds(1 << 30)); // Some time in the future that doesn't look like anything you'd expect.
        }

    }


    private static abstract class JobStepStatus extends StepStatus {

        private final JobStatus job;
        private final DeploymentStatus status;

        private JobStepStatus(StepType type, DeploymentSpec.Step step, List<StepStatus> dependencies, JobStatus job,
                                DeploymentStatus status) {
            super(type, step, dependencies, job.id().application().instance());
            this.job = requireNonNull(job);
            this.status = requireNonNull(status);
        }

        @Override
        public Optional<JobId> job() { return Optional.of(job.id()); }

        @Override
        public Optional<Instant> pausedUntil() {
            return status.application().require(job.id().application().instance()).jobPause(job.id().type());
        }

        @Override
        public Optional<Instant> coolingDownUntil(Change change) {
            if (job.lastTriggered().isEmpty()) return Optional.empty();
            if (job.lastCompleted().isEmpty()) return Optional.empty();
            if (job.firstFailing().isEmpty()) return Optional.empty();
            Versions lastVersions = job.lastCompleted().get().versions();
            if (change.platform().isPresent() && ! change.platform().get().equals(lastVersions.targetPlatform())) return Optional.empty();
            if (change.application().isPresent() && ! change.application().get().equals(lastVersions.targetApplication())) return Optional.empty();
            if (status.application.deploymentSpec().requireInstance(job.id().application().instance()).upgradePolicy() == DeploymentSpec.UpgradePolicy.canary) return Optional.empty();
            if (job.id().type().environment().isTest() && job.isOutOfCapacity()) return Optional.empty();

            Instant firstFailing = job.firstFailing().get().end().get();
            Instant lastCompleted = job.lastCompleted().get().end().get();

            return firstFailing.equals(lastCompleted) ? Optional.of(lastCompleted)
                                                      : Optional.of(lastCompleted.plus(Duration.ofMinutes(10))
                                                                                 .plus(Duration.between(firstFailing, lastCompleted)
                                                                                               .dividedBy(2)))
                    .filter(status.now::isBefore);
        }

        private static JobStepStatus ofProductionDeployment(DeclaredZone step, List<StepStatus> dependencies,
                                                            DeploymentStatus status, InstanceName instance, JobType jobType) {
            ZoneId zone = ZoneId.from(step.environment(), step.region().get());
            JobStatus job = status.instanceJobs(instance).get(jobType);
            Optional<Deployment> existingDeployment = Optional.ofNullable(status.application().require(instance)
                                                                                .deployments().get(zone));

            return new JobStepStatus(StepType.deployment, step, dependencies, job, status) {

                @Override
                public Optional<Instant> readyAt(Change change, Optional<JobId> dependent) {
                    return super.readyAt(change, Optional.of(job.id()))
                                .filter(__ -> status.isTested(job.id(), change));
                }

                /** Complete if deployment is on pinned version, and last successful deployment, or if given versions is strictly a downgrade, and this isn't forced by a pin. */
                @Override
                public Optional<Instant> completedAt(Change change, Optional<JobId> dependent) {
                    if (     change.isPinned()
                        &&   change.platform().isPresent()
                        && ! existingDeployment.map(Deployment::version).equals(change.platform()))
                        return Optional.empty();

                    Change fullChange = status.application().require(instance).change();
                    if (existingDeployment.map(deployment ->    ! (change.upgrades(deployment.version()) || change.upgrades(deployment.applicationVersion()))
                                                             &&   (fullChange.downgrades(deployment.version()) || fullChange.downgrades(deployment.applicationVersion())))
                                          .orElse(false))
                        return job.lastCompleted().flatMap(Run::end);

                    return job.lastSuccess()
                              .filter(run ->    change.platform().map(run.versions().targetPlatform()::equals).orElse(true)
                                             && change.application().map(run.versions().targetApplication()::equals).orElse(true))
                              .flatMap(Run::end);
                }
            };
        }

        private static JobStepStatus ofProductionTest(DeclaredTest step, List<StepStatus> dependencies,
                                                      DeploymentStatus status, InstanceName instance, JobType testType, JobType prodType) {
            JobStatus job = status.instanceJobs(instance).get(testType);
            return new JobStepStatus(StepType.test, step, dependencies, job, status) {
                @Override
                public Optional<Instant> completedAt(Change change, Optional<JobId> dependent) {
                    Versions versions = Versions.from(change, status.application, status.deploymentFor(job.id()), status.systemVersion);
                    return job.lastSuccess()
                              .filter(run -> versions.targetsMatch(run.versions()))
                              .filter(run -> ! status.jobs()
                                                     .instance(instance)
                                                     .type(prodType)
                                                     .lastCompleted().endedNoLaterThan(run.start())
                                                     .isEmpty())
                              .map(run -> run.end().get());
                }
            };
        }

        private static JobStepStatus ofTestDeployment(DeclaredZone step, List<StepStatus> dependencies,
                                                      DeploymentStatus status, InstanceName instance,
                                                      JobType jobType, boolean declared) {
            JobStatus job = status.instanceJobs(instance).get(jobType);
            return new JobStepStatus(StepType.test, step, dependencies, job, status) {
                @Override
                public Optional<Instant> completedAt(Change change, Optional<JobId> dependent) {
                    return RunList.from(job)
                                  .matching(run -> run.versions().targetsMatch(Versions.from(change,
                                                                                             status.application,
                                                                                             dependent.flatMap(status::deploymentFor),
                                                                                             status.systemVersion)))
                                  .status(RunStatus.success)
                                  .asList().stream()
                                  .map(run -> run.end().get())
                                  .max(naturalOrder());
                }

                @Override
                public boolean isDeclared() { return declared; }
            };
        }

    }

}
