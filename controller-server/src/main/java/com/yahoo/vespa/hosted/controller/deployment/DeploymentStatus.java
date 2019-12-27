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
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.application.Change;
import com.yahoo.vespa.hosted.controller.application.Deployment;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.yahoo.config.provision.Environment.prod;
import static com.yahoo.config.provision.Environment.staging;
import static com.yahoo.config.provision.Environment.test;
import static com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType.stagingTest;
import static com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType.systemTest;
import static java.util.Comparator.naturalOrder;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.groupingBy;
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
    private final Map<JobId, StepStatus> jobSteps;
    private final List<StepStatus> allSteps;

    public DeploymentStatus(Application application, Map<JobId, JobStatus> allJobs, SystemName system, Version systemVersion) {
        this.application = requireNonNull(application);
        this.allJobs = JobList.from(allJobs.values());
        this.system = system;
        this.systemVersion = systemVersion;
        List<StepStatus> allSteps = new ArrayList<>();
        this.jobSteps = jobDependencies(application.deploymentSpec(), allSteps);
        this.allSteps = List.copyOf(allSteps);
    }

    public Application application() {
        return application;
    }

    public JobList jobs() {
        return allJobs;
    }

    public boolean hasFailures() {
        return ! allJobs.failing()
                        .not().withStatus(RunStatus.outOfCapacity)
                        .isEmpty();
    }

    public Map<JobType, JobStatus> instanceJobs(InstanceName instance) {
        return allJobs.asList().stream()
                      .filter(job -> job.id().application().equals(application.id().instance(instance)))
                      .collect(Collectors.toUnmodifiableMap(job -> job.id().type(),
                                                         job -> job));
    }

    public Map<ApplicationId, JobList> instanceJobs() {
        return allJobs.asList().stream()
                      .collect(groupingBy(job -> job.id().application(),
                                          collectingAndThen(toUnmodifiableList(), JobList::from)));
    }

    /** Returns the set of jobs that need to run for the application's current change to be considered complete. */
    public Map<JobId, List<Versions>> jobsToRun() {
        Map<JobId, List<Versions>> jobs = jobsToRun(application().change());
        if (application.outstandingChange().isEmpty())
            return jobs;

        // Add test jobs for any outstanding change.
        var testJobs = jobsToRun(application.outstandingChange().onTopOf(application.change()))
                .entrySet().stream()
                .filter(entry -> ! entry.getKey().type().isProduction());

        return Stream.concat(jobs.entrySet().stream(), testJobs)
                     .collect(collectingAndThen(toMap(Map.Entry::getKey,
                                                      Map.Entry::getValue,
                                                      DeploymentStatus::union,
                                                      LinkedHashMap::new),
                                                ImmutableMap::copyOf));
    }

    /** Returns the set of jobs that need to run for the given change to be considered complete. */
    public Map<JobId, List<Versions>> jobsToRun(Change change) {
        Map<JobId, List<Versions>> jobs = new LinkedHashMap<>();
        addProductionJobs(jobs, change);
        addTests(jobs);
        return ImmutableMap.copyOf(jobs);
    }

    public Map<JobId, StepStatus> jobSteps() { return jobSteps; }

    public List<StepStatus> allSteps() { return allSteps; }

    public Optional<Deployment> deploymentFor(JobId job) {
        return Optional.ofNullable(application.require(job.application().instance())
                                              .deployments().get(job.type().zone(system)));
    }

    /**
     * True if the job has already been triggered on the given versions, or if all test types (systemTest, stagingTest),
     * restricted to the job's instance if declared in that instance, have successful runs on the given versions.
     */
    public boolean isTested(JobId job, Versions versions) {
        return    allJobs.triggeredOn(versions).get(job).isPresent()
                  || Stream.of(systemTest, stagingTest)
                           .noneMatch(testType -> declaredTest(job.application(), testType).map(__ -> allJobs.instance(job.application().instance()))
                                                                                           .orElse(allJobs)
                                                                                           .type(testType)
                                                                                           .successOn(versions).isEmpty());
    }

    private void addProductionJobs(Map<JobId, List<Versions>> jobs, Change change) {
        jobSteps.forEach((job, step) -> {
            Versions versions = Versions.from(change, application, deploymentFor(job), systemVersion);
            if (job.type().isProduction() && step.completedAt(change, versions).isEmpty())
                jobs.put(job, List.of(versions));
        });
    }

    private void addTests(Map<JobId, List<Versions>> jobs) {
        Map<JobId, List<Versions>> testJobs = new HashMap<>();
        for (JobType testType : List.of(systemTest, stagingTest)) {
            jobs.forEach((job, versions) -> {
                if (job.type().isDeployment()) {
                    declaredTest(job.application(), testType).ifPresent(testJob -> {
                        testJobs.merge(testJob,
                                       versions.stream()
                                               .filter(version -> allJobs.successOn(version).get(testJob).isEmpty())
                                               .collect(toUnmodifiableList()),
                                       DeploymentStatus::union);
                    });
                }
            });
            jobs.forEach((job, versionsList) -> {
                if (job.type().isDeployment())
                    testJobs.merge(new JobId(job.application(), testType),
                                   versionsList.stream()
                                               .filter(versions -> testJobs.keySet().stream()
                                                                           .noneMatch(test ->    test.type() == testType
                                                                                              && testJobs.get(test).contains(versions)))
                                               .filter(versions -> allJobs.successOn(versions).type(testType).isEmpty())
                                               .collect(toUnmodifiableList()),
                                   DeploymentStatus::union);
            });
        }
        jobs.putAll(testJobs);
    }

    private Optional<JobId> declaredTest(ApplicationId instanceId, JobType testJob) {
        JobId jobId = new JobId(instanceId, testJob);
        return jobSteps.get(jobId).isDeclared() ? Optional.of(jobId) : Optional.empty();
    }

    /** Returns a DAG of the dependencies between the primitive steps in the spec, with iteration order equal to declaration order. */
    private Map<JobId, StepStatus> jobDependencies(DeploymentSpec spec, List<StepStatus> allSteps) {
        if (DeploymentSpec.empty.equals(spec))
            return Map.of();

        Map<JobId, StepStatus> dependencies = new LinkedHashMap<>();
        List<StepStatus> previous = List.of();
        for (DeploymentSpec.Step step : spec.steps())
            previous = fillStep(dependencies, allSteps, step, previous, spec.instanceNames().get(0));

        return ImmutableMap.copyOf(dependencies);
    }

    /** Adds the primitive steps contained in the given step, which depend on the given previous primitives, to the dependency graph. */
    private List<StepStatus> fillStep(Map<JobId, StepStatus> dependencies, List<StepStatus> allSteps,
                                      DeploymentSpec.Step step, List<StepStatus> previous, InstanceName instance) {
        if (step.steps().isEmpty()) {
            if ( ! step.delay().isZero()) {
                StepStatus stepStatus = new DelayStatus((DeploymentSpec.Delay) step, previous);
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
            allSteps.add(stepStatus);
            dependencies.put(new JobId(application.id().instance(instance), jobType), stepStatus);
            return previous;
        }

        // TODO jonmv: Make instance status as well, including block-change and upgrade policy, to keep track of change;
        //             set it equal to application's when dependencies are completed.
        if (step instanceof DeploymentInstanceSpec) {
            instance = ((DeploymentInstanceSpec) step).name();
            for (JobType test : List.of(systemTest, stagingTest))
                dependencies.putIfAbsent(new JobId(application.id().instance(instance), test),
                                         JobStepStatus.ofTestDeployment(new DeclaredZone(test.environment()), List.of(),
                                                                        this, instance, test, false));
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

        /** An instance — completion marks a change as ready for the jobs contained in it. */
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
        private final Optional<InstanceName> instance;

        private StepStatus(StepType type, DeploymentSpec.Step step, List<StepStatus> dependencies) {
            this(type, step, dependencies, null);
        }

        private StepStatus(StepType type, DeploymentSpec.Step step, List<StepStatus> dependencies, InstanceName instance) {
            this.type = requireNonNull(type);
            this.step = requireNonNull(step);
            this.dependencies = List.copyOf(dependencies);
            this.instance = Optional.ofNullable(instance);
        }

        /** The type of step this is. */
        public final StepType type() { return type; }

        /** The step defining this. */
        public final DeploymentSpec.Step step() { return step; }

        /** The list of steps that need to be complete before this may start. */
        public final List<StepStatus> dependencies() { return dependencies; }

        /** The instance of this, if any. */
        public final Optional<InstanceName> instance() { return instance; }

        /** The id of the job this corresponds to, if any. */
        public Optional<JobId> job() { return Optional.empty(); }

        /** The time at which this is, or was, complete on the given change and / or versions. */
        public abstract Optional<Instant> completedAt(Change change, Versions versions);

        /** The time at which this step is ready to run the specified change and / or versions. */
        public Optional<Instant> readyAt(Change change, Versions versions) {
            return dependenciesCompletedAt(change, versions)
                    .map(ready -> Stream.concat(Stream.of(blockedUntil(change),
                                                          pausedUntil(),
                                                          coolingDownUntil(versions))
                                                      .flatMap(Optional::stream),
                                                Stream.of(ready))
                                        .max(naturalOrder()).get());
        }

        /** The time at which all dependencies completed on the given change and / or versions. */
        public Optional<Instant> dependenciesCompletedAt(Change change, Versions versions) {
            return dependencies.stream().allMatch(step -> step.completedAt(change, versions).isPresent())
                   ? dependencies.stream().map(step -> step.completedAt(change, versions).get())
                                 .max(naturalOrder())
                                 .or(() -> Optional.of(Instant.EPOCH))
                   : Optional.empty();
        }

        /** The time until which this step is blocked by a change blocker. */
        // TODO jonmv: readyAt for instance can be delayed by block window. Upgrade policy / confidence is something different.
        public Optional<Instant> blockedUntil(Change change) { return Optional.empty(); }

        /** The time until which this step is paused by user intervention. */
        public Optional<Instant> pausedUntil() { return Optional.empty(); }

        /** The time until which this step is cooling down, due to consecutive failures. */
        public Optional<Instant> coolingDownUntil(Versions versions) { return Optional.empty(); }

        /** Whether this step is currently running, with the given version parameters. */
        public abstract boolean isRunning(Versions versions);

        /** Whether this step is declared in the deployment spec, or is an implicit step. */
        public boolean isDeclared() { return true; }

    }


    private static class DelayStatus extends StepStatus {

        private DelayStatus(DeploymentSpec.Delay step, List<StepStatus> dependencies) {
            super(StepType.delay, step, dependencies);
        }

        @Override
        public Optional<Instant> completedAt(Change change, Versions versions) {
            return readyAt(change, versions).map(completion -> completion.plus(step().delay()));
        }

        @Override
        public boolean isRunning(Versions versions) {
            return true;
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
        public boolean isRunning(Versions versions) {
            return job.isRunning() && job.lastTriggered().get().versions().targetsMatch(versions);
        }

        @Override
        public Optional<Instant> pausedUntil() {
            return status.application().require(job.id().application().instance()).jobPause(job.id().type());
        }

        @Override
        public Optional<Instant> coolingDownUntil(Versions versions) {
            if (job.lastTriggered().isEmpty()) return Optional.empty();
            if (job.lastCompleted().isEmpty()) return Optional.empty();
            if (job.firstFailing().isEmpty()) return Optional.empty();
            if ( ! versions.targetsMatch(job.lastCompleted().get().versions())) return Optional.empty();
            if (status.application.deploymentSpec().requireInstance(job.id().application().instance()).upgradePolicy() == DeploymentSpec.UpgradePolicy.canary) return Optional.empty();
            if (job.id().type().environment().isTest() && job.isOutOfCapacity()) return Optional.empty();

            Instant firstFailing = job.firstFailing().get().end().get();
            Instant lastCompleted = job.lastCompleted().get().end().get();

            return firstFailing.equals(lastCompleted) ? Optional.of(lastCompleted)
                                                      : Optional.of(lastCompleted.plus(Duration.ofMinutes(10))
                                                                                 .plus(Duration.between(firstFailing, lastCompleted)
                                                                                               .dividedBy(2)));
        }

        private static JobStepStatus ofProductionDeployment(DeclaredZone step, List<StepStatus> dependencies,
                                                            DeploymentStatus status, InstanceName instance, JobType jobType) {
            ZoneId zone = ZoneId.from(step.environment(), step.region().get());
            JobStatus job = status.instanceJobs(instance).get(jobType);
            Optional<Deployment> existingDeployment = Optional.ofNullable(status.application().require(instance)
                                                                                .deployments().get(zone));

            return new JobStepStatus(StepType.deployment, step, dependencies, job, status) {

                @Override
                public Optional<Instant> readyAt(Change change, Versions versions) {
                    return super.readyAt(change, versions)
                                .filter(__ -> status.isTested(job.id(), versions));
                }

                /** Complete if deployment is on pinned version, and last successful deployment, or if given versions is strictly a downgrade, and this isn't forced by a pin. */
                @Override
                public Optional<Instant> completedAt(Change change, Versions versions) {
                    if (     change.isPinned()
                             &&   change.platform().isPresent()
                             && ! existingDeployment.map(Deployment::version).equals(change.platform()))
                        return Optional.empty();

                    Change fullChange = status.application().change();
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
                                                      DeploymentStatus status, InstanceName instance, JobType testType, JobType jobType) {
            JobStatus job = status.instanceJobs(instance).get(testType);
            return new JobStepStatus(StepType.test, step, dependencies, job, status) {
                @Override
                public Optional<Instant> completedAt(Change change, Versions versions) {
                    return job.lastSuccess()
                              .filter(run -> versions.targetsMatch(run.versions()))
                              .filter(run -> status.instanceJobs(instance).get(jobType).lastCompleted()
                                                   .map(last -> ! last.end().get().isAfter(run.start())).orElse(false))
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
                public Optional<Instant> completedAt(Change change, Versions versions) {
                    return RunList.from(job)
                                  .on(versions)
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
