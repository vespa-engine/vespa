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
import static java.util.Comparator.naturalOrder;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toUnmodifiableList;
import static java.util.stream.Collectors.toUnmodifiableMap;

/**
 * Status of the deployment jobs of an {@link Application}.
 *
 * @author jonmv
 */
public class DeploymentStatus {

    private final Application application;
    private final JobList allJobs;
    private final SystemName system;
    private final Version systemVersion;
    private final Map<JobId, StepStatus> steps;

    public DeploymentStatus(Application application, Map<JobId, JobStatus> allJobs, SystemName system, Version systemVersion) {
        this.application = requireNonNull(application);
        this.allJobs = JobList.from(allJobs.values());
        this.system = system;
        this.systemVersion = systemVersion;
        this.steps = jobDependencies(application.deploymentSpec());
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
        return jobsToRun(application().change());
    }

    /** Returns the set of jobs that need to run for the given change to be considered complete. */
    public Map<JobId, List<Versions>> jobsToRun(Change change) {
        Map<JobId, List<Versions>> jobs = new LinkedHashMap<>();

        addProductionJobs(jobs, change);
        addTests(jobs);
        if (jobs.isEmpty())
            addTestsOnly(jobs, change);

        return ImmutableMap.copyOf(jobs);
    }

    public Map<JobId, StepStatus> stepStatus() { return steps; }

    private void addProductionJobs(Map<JobId, List<Versions>> jobs, Change change) {
        steps.forEach((job, step) -> {
            Versions versions = Versions.from(change, application, deploymentFor(job), systemVersion);
            if (     job.type().isProduction()
                &&   step.completedAt(change).isEmpty()
                && ! step.isRunning(versions))
                jobs.put(job, List.of(versions));
        });
    }

    private void addTests(Map<JobId, List<Versions>> jobs) {
        Map<JobId, List<Versions>> testJobs = new HashMap<>();
        jobs.forEach((job, versions) -> {
            if ( ! job.type().isTest()) {
                declaredTest(job.application(), JobType.systemTest).ifPresent(test -> {
                    testJobs.merge(test,
                                   versions.stream()
                                           .filter(version -> ! steps.get(test).isRunning(version))
                                           .filter(version -> allJobs.successOn(version).get(test).isEmpty() && allJobs.triggeredOn(version).get(job).isEmpty())
                                           .collect(toUnmodifiableList()),
                                   DeploymentStatus::concat);
                });
                declaredTest(job.application(), JobType.stagingTest).ifPresent(test -> {
                    testJobs.merge(test,
                                   versions.stream()
                                           .filter(version -> ! steps.get(test).isRunning(version))
                                           .filter(version -> allJobs.successOn(version).get(test).isEmpty() && allJobs.triggeredOn(version).get(job).isEmpty())
                                           .collect(toUnmodifiableList()),
                                   DeploymentStatus::concat);
                });
            }
        });
        jobs.forEach((job, versions) -> {
            if ( ! job.type().isTest() && ! testedOn(versions, JobType.systemTest, testJobs))
                testJobs.merge(new JobId(job.application(), JobType.systemTest),
                               versions.stream()
                                       .filter(version -> steps.keySet().stream().noneMatch(id -> id.type() == JobType.systemTest && steps.get(id).isRunning(version)))
                                       .filter(version -> allJobs.successOn(version).type(JobType.systemTest).isEmpty() && allJobs.triggeredOn(version).get(job).isEmpty())
                                       .collect(toUnmodifiableList()),
                               DeploymentStatus::concat);
            if ( ! job.type().isTest() && ! testedOn(versions, JobType.stagingTest, testJobs))
                testJobs.merge(new JobId(job.application(), JobType.stagingTest),
                               versions.stream()
                                       .filter(version -> steps.keySet().stream().noneMatch(id -> id.type() == JobType.stagingTest && steps.get(id).isRunning(version)))
                                       .filter(version -> allJobs.successOn(version).type(JobType.stagingTest).isEmpty() && allJobs.triggeredOn(version).get(job).isEmpty())
                                       .collect(toUnmodifiableList()),
                               DeploymentStatus::concat);
        });
        jobs.putAll(testJobs);
    }

    private Optional<JobId> declaredTest(ApplicationId instanceId, JobType testJob) {
        return steps.keySet().stream()
                    .filter(job -> job.type() == testJob)
                    .filter(job -> job.application().equals(instanceId))
                    .findAny();
    }

    private boolean testedOn(List<Versions> versions, JobType testJob, Map<JobId, List<Versions>> testJobs) {
        return testJobs.keySet().stream()
                       .anyMatch(job -> job.type() == testJob && testJobs.get(job).containsAll(versions));
    }

    private void addTestsOnly(Map<JobId, List<Versions>> jobs, Change change) {
        /*steps.forEach((job, step) -> {
            if (List.of(test, staging).contains(job.type().environment()))
                jobs.put(job, List.of(Versions.from(change, application, Optional.empty(), systemVersion)));
        });*/
    }

    private  static <T> List<T> concat(List<T> first, List<T> second) {
        return Stream.concat(first.stream(), second.stream()).collect(toUnmodifiableList());
    }

    private Optional<Deployment> deploymentFor(JobId job) {
        return Optional.ofNullable(application.require(job.application().instance())
                                              .deployments().get(job.type().zone(system)));
    }

    /** Returns a DAG of the dependencies between the primitive steps in the spec, with iteration order equal to declaration order. */
    Map<JobId, StepStatus> jobDependencies(DeploymentSpec spec) {
        Map<JobId, StepStatus> dependencies = new LinkedHashMap<>();
        List<StepStatus> previous = List.of();
        for (DeploymentSpec.Step step : spec.steps())
            previous = fillStep(dependencies, step, previous, spec.instanceNames().get(0));

        return ImmutableMap.copyOf(dependencies);
    }

    /** Adds the primitive steps contained in the given step, which depend on the given previous primitives, to the dependency graph. */
    List<StepStatus> fillStep(Map<JobId, StepStatus> dependencies, DeploymentSpec.Step step,
                              List<StepStatus> previous, InstanceName instance) {
        if (step.steps().isEmpty()) {
            if ( ! step.delay().isZero())
                return List.of(new DelayStatus((DeploymentSpec.Delay) step, previous));

            JobType jobType;
            StepStatus stepStatus;
            if (step.concerns(test) || step.concerns(staging)) { // SKIP?
                jobType = JobType.from(system, ((DeclaredZone) step).environment(), null)
                                 .orElseThrow(() -> new IllegalStateException("No job is known for " + step + " in " + system));
                stepStatus = JobStepStatus.ofTestDeployment((DeclaredZone) step, List.of(), this, instance, jobType);
            }
            else if (step.isTest()) {
                jobType = JobType.from(system, ((DeclaredTest) step).region())
                                          .orElseThrow(() -> new IllegalStateException("No job is known for " + step + " in " + system));
                JobType preType = JobType.from(system, prod, ((DeclaredTest) step).region())
                                         .orElseThrow(() -> new IllegalStateException("No job is known for " + step + " in " + system));
                stepStatus = JobStepStatus.ofProductionTest((DeclaredTest) step, previous, this, instance, jobType, preType);
                previous = List.of(stepStatus);
            }
            else {
                jobType = JobType.from(system, ((DeclaredZone) step).environment(), ((DeclaredZone) step).region().get())
                                         .orElseThrow(() -> new IllegalStateException("No job is known for " + step + " in " + system));
                stepStatus = JobStepStatus.ofProductionDeployment((DeclaredZone) step, previous, this, instance, jobType);
                previous = List.of(stepStatus);
            }
            dependencies.put(new JobId(application.id().instance(instance), jobType), stepStatus);
            return previous;
        }

        Optional<InstanceName> stepInstance = Optional.of(step)
                                                      .filter(DeploymentInstanceSpec.class::isInstance)
                                                      .map(DeploymentInstanceSpec.class::cast)
                                                      .map(DeploymentInstanceSpec::name);
        if (step.isOrdered()) {
            for (DeploymentSpec.Step nested : step.steps())
                previous = fillStep(dependencies, nested, previous, stepInstance.orElse(instance));

            return previous;
        }

        List<StepStatus> parallel = new ArrayList<>();
        for (DeploymentSpec.Step nested : step.steps())
            parallel.addAll(fillStep(dependencies, nested, previous, stepInstance.orElse(instance)));

        return List.copyOf(parallel);
    }

    private boolean isTested(JobId job, Versions versions) {
        return      allJobs.triggeredOn(versions).get(job).isPresent()
               || ! declaredTest(job.application(), JobType.systemTest).map(__ -> allJobs.instance(job.application().instance()))
                                                                       .orElse(allJobs)
                                                                       .type(JobType.systemTest)
                                                                       .successOn(versions).isEmpty()
               && ! declaredTest(job.application(), JobType.stagingTest).map(__ -> allJobs.instance(job.application().instance()))
                                                                        .orElse(allJobs)
                                                                        .type(JobType.stagingTest)
                                                                        .successOn(versions).isEmpty();
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

        private final DeploymentSpec.Step step;
        private final List<StepStatus> dependencies;
        private final Optional<InstanceName> instance;

        protected StepStatus(DeploymentSpec.Step step, List<StepStatus> dependencies) {
            this(step, dependencies, null);
        }

        protected StepStatus(DeploymentSpec.Step step, List<StepStatus> dependencies, InstanceName instance) {
            this.step = requireNonNull(step);
            this.dependencies = List.copyOf(dependencies);
            this.instance = Optional.ofNullable(instance);
        }

        /** The step defining this. */
        public final DeploymentSpec.Step step() { return step; }

        /** The list of steps that need to be complete before this may start. */
        public final List<StepStatus> dependencies() { return dependencies; }

        /** The instance of this, if any. */
        public final Optional<InstanceName> instance() { return instance; }

        /** The time at which this is complete on the given versions. */
        public abstract Optional<Instant> completedAt(Change change);

        /** The time at which all dependencies completed on the given version. */
        public Optional<Instant> readyAt(Change change) {
            return dependencies.stream().allMatch(step -> step.completedAt(change).isPresent())
                   ? dependencies.stream().map(step -> step.completedAt(change).get())
                                 .max(naturalOrder())
                                 .or(() -> Optional.of(Instant.EPOCH))
                   : Optional.empty();
        }

        public abstract boolean isRunning(Versions versions);

    }


    public static class DelayStatus extends StepStatus {

        public DelayStatus(DeploymentSpec.Delay step, List<StepStatus> dependencies) {
            super(step, dependencies);
        }

        @Override
        public Optional<Instant> completedAt(Change change) {
            return readyAt(change).map(completion -> completion.plus(step().delay()));
        }

        @Override
        public boolean isRunning(Versions versions) {
            return true;
        }

    }


    public static abstract class JobStepStatus extends StepStatus {

        private final JobStatus job;

        protected JobStepStatus(DeploymentSpec.Step step, List<StepStatus> dependencies, JobStatus job) {
            super(step, dependencies, job.id().application().instance());
            this.job = requireNonNull(job);
        }

        @Override
        public boolean isRunning(Versions versions) {
            return job.isRunning() && job.lastTriggered().get().versions().targetsMatch(versions);
        }

        public static JobStepStatus ofProductionDeployment(DeclaredZone step, List<StepStatus> dependencies,
                                                           DeploymentStatus status, InstanceName instance, JobType jobType) {
            ZoneId zone = ZoneId.from(step.environment(), step.region().get());
            JobStatus job = status.instanceJobs(instance).get(jobType);
            Optional<Deployment> existingDeployment = Optional.ofNullable(status.application().require(instance)
                                                                                .deployments().get(zone));

            return new JobStepStatus(step, dependencies, job) {
                @Override
                public Optional<Instant> readyAt(Change change) {
                    Versions versions = Versions.from(change, status.application, existingDeployment, status.systemVersion);
                    Optional<Instant> readyAt = super.readyAt(change)
                                                     .filter(__ -> status.isTested(job.id(), versions));
                    if (readyAt.isEmpty())
                        return Optional.empty();

                    Optional<Instant> pausedUntil = status.application.require(instance).jobPause(jobType);
                    if (pausedUntil.isPresent() && pausedUntil.get().isAfter(readyAt.get()))
                        return pausedUntil;

                    if (job.lastTriggered().isEmpty()) return readyAt;
                    if (job.isSuccess()) return readyAt;
                    if (job.lastCompleted().isEmpty()) return readyAt;
                    if (job.firstFailing().isEmpty()) return readyAt;
                    if ( ! versions.targetsMatch(job.lastCompleted().get().versions())) return readyAt;
                    if (status.application.deploymentSpec().requireInstance(instance).upgradePolicy() == DeploymentSpec.UpgradePolicy.canary) return readyAt;
                    if (jobType.environment().isTest() && job.isOutOfCapacity()) return readyAt;

                    Instant firstFailing = job.firstFailing().get().end().get();
                    Instant lastCompleted = job.lastCompleted().get().end().get();
                    return Optional.of(lastCompleted.plus(Duration.ofMinutes(10))
                                                    .plus(Duration.between(firstFailing, lastCompleted)
                                                                  .dividedBy(2)));
                }

                /** Complete if deployment is on pinned version, and last successful deployment, or if given versions is strictly a downgrade, and this isn't forced by a pin. */
                @Override
                public Optional<Instant> completedAt(Change change) {
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

        public static JobStepStatus ofProductionTest(DeclaredTest step, List<StepStatus> dependencies,
                                                     DeploymentStatus status, InstanceName instance, JobType testType, JobType jobType) {
            JobStatus job = status.instanceJobs(instance).get(testType);
            return new JobStepStatus(step, dependencies, job) {
                @Override
                public Optional<Instant> completedAt(Change change) {
                    Versions toVerify = Versions.from(change, status.application.require(instance).deployments().get(ZoneId.from(prod, step.region())));
                    return job.lastSuccess()
                              .filter(run -> toVerify.targetsMatch(run.versions()))
                              .filter(run -> status.instanceJobs(instance).get(jobType).lastCompleted()
                                                   .map(last -> last.end().get().isBefore(run.start())).orElse(false))
                              .map(run -> run.end().get());
                }
            };
        }

        public static JobStepStatus ofTestDeployment(DeclaredZone step, List<StepStatus> dependencies,
                                                     DeploymentStatus status, InstanceName instance, JobType jobType) {
            Versions versions = Versions.from(status.application, status.systemVersion);
            JobStatus job = status.instanceJobs(instance).get(jobType);
            return new JobStepStatus(step, dependencies, job) {
                @Override
                public Optional<Instant> completedAt(Change change) {
                    return RunList.from(job)
                                  .on(versions)
                                  .status(RunStatus.success)
                                  .asList().stream()
                                  .map(run -> run.end().get())
                                  .max(naturalOrder());
                }
            };
        }

    }

    /*
     *  Compute all JobIds to run: test and staging for first instance, unless declared in a parallel instance.
     *  Create StepStatus for the first two, then
     *
     *
     *
     *
     *
     *
     *
     *
     *
     *
     * Prod:        completedAt: change, fullChange
     * Test:        completedAt: versions?
     * Delay:       completedAt: change, fullChange
     * Any:         readyAt: change -> versions
     * Any:         testedAt: versions
     *
     * Start with system and staging for first instance as dependencies.
     * Declared test jobs replace these for intra-instance dependents.
     * Test and staging are implicitly parallel. (Well, they already are, if they don't depend on each other.)
     *
     * Map JobId to StepStatus:
     * For each prod JobId: Compute Optional<Versions> to run for current change to be done.
     * Prod jobs may wait for other prod jobs' to-do runs, but ignores their versions.
     * Prod jobs may wait for test jobs, considering their versions.
     * For each prod job, if job already triggered on desired versions, ignore the below.
     * For each prod job, add other prod job dependencies.
     * For each prod job, add explicit tests in same instance.
     * For each prod versions to run, find all prod jobs for which those versions aren't tested (before the job), then
     *      for each such set of jobs, find the last common dependency instance, and add the test for that, or
     *      for each such set of jobs, add tests for those versions with the first declared instance; in any case
     *      add all implicit tests to some structure for tracking.
     *
     * Eliminate already running jobs.
     * Keep set of JobId x Versions for each StepStatus, in topological order, for starting jobs and for display.
     * DepTri: Needs all jobs to run that are also ready. Test jobs are always ready.
     * API: Needs all jobs to run, and what they are waiting for, like, delay (until), or other jobs, or pause.
     *      To find dependency jobs, DFS and sort by topological order.
     *
     * anySysTest && anyStaTest || triggeredProd
     *
     */

}
