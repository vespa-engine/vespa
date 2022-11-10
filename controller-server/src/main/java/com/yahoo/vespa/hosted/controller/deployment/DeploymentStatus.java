// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.deployment;

import com.google.common.collect.ImmutableMap;
import com.yahoo.component.Version;
import com.yahoo.component.VersionCompatibility;
import com.yahoo.config.application.api.DeploymentInstanceSpec;
import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.application.api.DeploymentSpec.DeclaredTest;
import com.yahoo.config.application.api.DeploymentSpec.DeclaredZone;
import com.yahoo.config.application.api.DeploymentSpec.UpgradePolicy;
import com.yahoo.config.application.api.DeploymentSpec.UpgradeRollout;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.zone.ZoneApi;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.stream.CustomCollectors;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.Instance;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.ApplicationVersion;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RevisionId;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneRegistry;
import com.yahoo.vespa.hosted.controller.application.Change;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.versions.VersionStatus;
import com.yahoo.vespa.hosted.controller.versions.VespaVersion;
import com.yahoo.vespa.hosted.controller.versions.VespaVersion.Confidence;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.yahoo.collections.Iterables.reversed;
import static com.yahoo.config.application.api.DeploymentSpec.RevisionTarget.next;
import static com.yahoo.config.provision.Environment.prod;
import static com.yahoo.config.provision.Environment.staging;
import static com.yahoo.config.provision.Environment.test;
import static com.yahoo.vespa.hosted.controller.deployment.RunStatus.invalidApplication;
import static java.util.Comparator.comparing;
import static java.util.Comparator.naturalOrder;
import static java.util.Comparator.reverseOrder;
import static java.util.Objects.requireNonNull;
import static java.util.function.BinaryOperator.maxBy;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static java.util.stream.Collectors.toUnmodifiableList;

/**
 * Status of the deployment jobs of an {@link Application}.
 *
 * @author jonmv
 */
public class DeploymentStatus {

    private static <T> List<T> union(List<T> first, List<T> second) {
        return Stream.concat(first.stream(), second.stream()).distinct().collect(toUnmodifiableList());
    }

    private final Application application;
    private final JobList allJobs;
    private final VersionStatus versionStatus;
    private final Version systemVersion;
    private final Function<InstanceName, VersionCompatibility> versionCompatibility;
    private final ZoneRegistry zones;
    private final Instant now;
    private final Map<JobId, StepStatus> jobSteps;
    private final List<StepStatus> allSteps;

    public DeploymentStatus(Application application, Function<JobId, JobStatus> allJobs, ZoneRegistry zones, VersionStatus versionStatus,
                            Version systemVersion, Function<InstanceName, VersionCompatibility> versionCompatibility, Instant now) {
        this.application = requireNonNull(application);
        this.zones = zones;
        this.versionStatus = requireNonNull(versionStatus);
        this.systemVersion = requireNonNull(systemVersion);
        this.versionCompatibility = versionCompatibility;
        this.now = requireNonNull(now);
        List<StepStatus> allSteps = new ArrayList<>();
        Map<JobId, JobStatus> jobs = new HashMap<>();
        this.jobSteps = jobDependencies(application.deploymentSpec(), allSteps, job -> jobs.computeIfAbsent(job, allJobs));
        this.allSteps = Collections.unmodifiableList(allSteps);
        this.allJobs = JobList.from(jobSteps.keySet().stream().map(allJobs).collect(toList()));
    }

    private JobType systemTest(JobType dependent) {
        return JobType.systemTest(zones, dependent == null ? null : findCloud(dependent));
    }

    private JobType stagingTest(JobType dependent) {
        return JobType.stagingTest(zones, dependent == null ? null : findCloud(dependent));
    }

    /** The application this deployment status concerns. */
    public Application application() {
        return application;
    }

    /** A filterable list of the status of all jobs for this application. */
    public JobList jobs() {
        return allJobs;
    }

    /** Whether any jobs both dependent on the dependency, and a dependency for the dependent, are failing. */
    private boolean hasFailures(StepStatus dependency, StepStatus dependent) {
        Set<StepStatus> dependents = new HashSet<>();
        fillDependents(dependency, new HashSet<>(), dependents, dependent);
        Set<JobId> criticalJobs = dependents.stream().flatMap(step -> step.job().stream()).collect(toSet());

        return ! allJobs.matching(job -> criticalJobs.contains(job.id()))
                        .failingHard()
                        .isEmpty();
    }

    private boolean fillDependents(StepStatus dependency, Set<StepStatus> visited, Set<StepStatus> dependents, StepStatus current) {
        if (visited.contains(current))
            return dependents.contains(current);

        if (dependency == current)
            dependents.add(current);
        else
            for (StepStatus dep : current.dependencies)
                if (fillDependents(dependency, visited, dependents, dep))
                    dependents.add(current);

        visited.add(current);
        return dependents.contains(current);
    }

    /** Whether any job is failing on versions selected by the given filter, with errors other than lack of capacity in a test zone.. */
    public boolean hasFailures(Predicate<RevisionId> revisionFilter) {
        return ! allJobs.failingHard()
                        .matching(job -> revisionFilter.test(job.lastTriggered().get().versions().targetRevision()))
                        .isEmpty();
    }

    /** Whether any jobs of this application are failing with other errors than lack of capacity in a test zone. */
    public boolean hasFailures() {
        return ! allJobs.failingHard().isEmpty();
    }

    /** All job statuses, by job type, for the given instance. */
    public Map<JobType, JobStatus> instanceJobs(InstanceName instance) {
        return allJobs.asList().stream()
                      .filter(job -> job.id().application().equals(application.id().instance(instance)))
                      .collect(CustomCollectors.toLinkedMap(job -> job.id().type(), Function.identity()));
    }

    /** Filterable job status lists for each instance of this application. */
    public Map<ApplicationId, JobList> instanceJobs() {
        return allJobs.groupingBy(job -> job.id().application());
    }

    /** Returns change potentially with a compatibility platform added, if required for the change to roll out to the given instance. */
    public Change withPermittedPlatform(Change change, InstanceName instance, boolean allowOutdatedPlatform) {
        Change augmented = withCompatibilityPlatform(change, instance);
        if (allowOutdatedPlatform)
            return augmented;

        // If compatibility platform is present, require that jobs have previously been run on that platform's major.
        // If platform is not present, app is already on the (old) platform iff. it has production deployments.
        boolean alreadyDeployedOnPlatform = augmented.platform().map(platform -> allJobs.production().asList().stream()
                                                                                        .anyMatch(job -> job.runs().values().stream()
                                                                                                            .anyMatch(run -> run.versions().targetPlatform().getMajor() == platform.getMajor())))
                                                     .orElse( ! application.productionDeployments().values().stream().allMatch(List::isEmpty));

        // Verify target platform is either current, or was previously deployed for this app.
        if (augmented.platform().isPresent() && ! versionStatus.isOnCurrentMajor(augmented.platform().get()) && ! alreadyDeployedOnPlatform)
            throw new IllegalArgumentException("platform version " + augmented.platform().get() + " is not on a current major version in this system");

        Version latestHighConfidencePlatform = null;
        for (VespaVersion platform : versionStatus.deployableVersions())
            if (platform.confidence().equalOrHigherThan(Confidence.high))
                latestHighConfidencePlatform = platform.versionNumber();

        // Verify package is compatible with the current major, or newer, or that there already are deployments on a compatible, outdated platform.
        if (latestHighConfidencePlatform != null) {
            Version target = latestHighConfidencePlatform;
            augmented.revision().flatMap(revision -> application.revisions().get(revision).compileVersion())
                     .filter(target::isAfter)
                     .ifPresent(compiled -> {
                         if (versionCompatibility.apply(instance).refuse(target, compiled) && ! alreadyDeployedOnPlatform)
                             throw new IllegalArgumentException("compile version " + compiled + " is incompatible with the current major version of this system");
                     });
        }

        return augmented;
    }

    private Change withCompatibilityPlatform(Change change, InstanceName instance) {
        if (change.revision().isEmpty())
            return change;

        Optional<Version> compileVersion = change.revision()
                                                 .map(application.revisions()::get)
                                                 .flatMap(ApplicationVersion::compileVersion);

        // If the revision requires a certain platform for compatibility, add that here, unless we're already deploying a compatible platform.
        VersionCompatibility compatibility = versionCompatibility.apply(instance);
        Predicate<Version> compatibleWithCompileVersion = version -> compileVersion.map(compiled -> compatibility.accept(version, compiled)).orElse(true);
        if (change.platform().map(compatibleWithCompileVersion::test).orElse(false))
            return change;

        if (   application.productionDeployments().isEmpty()
            || application.productionDeployments().getOrDefault(instance, List.of()).stream()
                          .anyMatch(deployment -> ! compatibleWithCompileVersion.test(deployment.version()))) {
            for (Version platform : targetsForPolicy(versionStatus, systemVersion, application.deploymentSpec().requireInstance(instance).upgradePolicy()))
                if (compatibleWithCompileVersion.test(platform))
                    return change.withoutPin().with(platform);
        }
        return change;
    }

    /** Returns target versions for given confidence, by descending version number. */
    public static List<Version> targetsForPolicy(VersionStatus versions, Version systemVersion, DeploymentSpec.UpgradePolicy policy) {
        if (policy == DeploymentSpec.UpgradePolicy.canary)
            return List.of(systemVersion);

        VespaVersion.Confidence target = policy == DeploymentSpec.UpgradePolicy.defaultPolicy ? VespaVersion.Confidence.normal : VespaVersion.Confidence.high;
        return versions.deployableVersions().stream()
                       .filter(version -> version.confidence().equalOrHigherThan(target))
                       .map(VespaVersion::versionNumber)
                       .sorted(reverseOrder())
                       .collect(Collectors.toList());
    }


    /**
     * The set of jobs that need to run for the changes of each instance of the application to be considered complete,
     * and any test jobs for any outstanding change, which will likely be needed to later deploy this change.
     */
    public Map<JobId, List<Job>> jobsToRun() {
        if (application.revisions().last().isEmpty()) return Map.of();

        Map<InstanceName, Change> changes = new LinkedHashMap<>();
        for (InstanceName instance : application.deploymentSpec().instanceNames())
            changes.put(instance, application.require(instance).change());
        Map<JobId, List<Job>> jobs = jobsToRun(changes);

        // Add test jobs for any outstanding change.
        Map<InstanceName, Change> outstandingChanges = new LinkedHashMap<>();
        for (InstanceName instance : application.deploymentSpec().instanceNames()) {
            Change outstanding = outstandingChange(instance);
            if (outstanding.hasTargets())
                outstandingChanges.put(instance, outstanding.onTopOf(application.require(instance).change()));
        }
        var testJobs = jobsToRun(outstandingChanges, true).entrySet().stream()
                                                          .filter(entry -> ! entry.getKey().type().isProduction());

        return Stream.concat(jobs.entrySet().stream(), testJobs)
                     .collect(collectingAndThen(toMap(Map.Entry::getKey,
                                                      Map.Entry::getValue,
                                                      DeploymentStatus::union,
                                                      LinkedHashMap::new),
                                                Collections::unmodifiableMap));
    }

    private Map<JobId, List<Job>> jobsToRun(Map<InstanceName, Change> changes, boolean eagerTests) {
        if (application.revisions().last().isEmpty()) return Map.of();

        Map<JobId, List<Job>> productionJobs = new LinkedHashMap<>();
        changes.forEach((instance, change) -> productionJobs.putAll(productionJobs(instance, change, eagerTests)));
        Map<JobId, List<Job>> testJobs = testJobs(productionJobs);
        Map<JobId, List<Job>> jobs = new LinkedHashMap<>(testJobs);
        jobs.putAll(productionJobs);
        // Add runs for idle, declared test jobs if they have no successes on their instance's change's versions.
        jobSteps.forEach((job, step) -> {
            if ( ! step.isDeclared() || job.type().isProduction() || jobs.containsKey(job))
                return;

            Change change = changes.get(job.application().instance());
            if (change == null || ! change.hasTargets())
                return;

            Map<CloudName, Optional<JobId>> firstProductionJobsWithDeployment = firstDependentProductionJobsWithDeployment(job.application().instance());
            firstProductionJobsWithDeployment.forEach((cloud, firstProductionJobWithDeploymentInCloud) -> {
                Versions versions = Versions.from(change,
                                                  application,
                                                  firstProductionJobWithDeploymentInCloud.flatMap(this::deploymentFor),
                                                  fallbackPlatform(change, job));
                if (step.completedAt(change, firstProductionJobWithDeploymentInCloud).isEmpty()) {
                    JobType typeWithZone = job.type().isSystemTest() ? JobType.systemTest(zones, cloud) : JobType.stagingTest(zones, cloud);
                    jobs.merge(job, List.of(new Job(typeWithZone, versions, step.readyAt(change), change)), DeploymentStatus::union);
                }
            });
        });
        return Collections.unmodifiableMap(jobs);
    }

    /**
     * Returns the clouds, and their first production deployments, that depend on this instance; or,
     * if no such deployments exist, all clouds the application deploy to, and their first production deployments; or
     * if no clouds are deployed to at all, the system default cloud.
     */
    public Map<CloudName, Optional<JobId>> firstDependentProductionJobsWithDeployment(InstanceName testInstance) {
        // Find instances' dependencies on each other: these are topologically ordered, so a simple traversal does it.
        Map<InstanceName, Set<InstanceName>> dependencies = new HashMap<>();
        instanceSteps().forEach((name, step) -> {
            dependencies.put(name, new HashSet<>());
            dependencies.get(name).add(name);
            for (StepStatus dependency : step.dependencies()) {
                dependencies.get(name).add(dependency.instance());
                dependencies.get(name).addAll(dependencies.get(dependency.instance));
            }
        });

        Map<CloudName, Optional<JobId>> independentJobsPerCloud = new HashMap<>();
        Map<CloudName, Optional<JobId>> jobsPerCloud = new HashMap<>();
        jobSteps.forEach((job, step) -> {
            if ( ! job.type().isProduction() || ! job.type().isDeployment())
                return;

            (dependencies.get(step.instance()).contains(testInstance) ? jobsPerCloud
                                                                      : independentJobsPerCloud)
                    .merge(findCloud(job.type()),
                           Optional.of(job),
                           (o, n) -> o.filter(v -> deploymentFor(v).isPresent())             // Keep first if its deployment is present.
                                      .or(() -> n.filter(v -> deploymentFor(v).isPresent())) // Use next if only its deployment is present.
                                      .or(() -> o));                                         // Keep first if none have deployments.
        });

        if (jobsPerCloud.isEmpty())
            jobsPerCloud.putAll(independentJobsPerCloud);

        if (jobsPerCloud.isEmpty())
            jobsPerCloud.put(zones.systemZone().getCloudName(), Optional.empty());

        return jobsPerCloud;
    }


    /** Fall back to the newest, deployable platform, which is compatible with what we want to deploy. */
    public Supplier<Version> fallbackPlatform(Change change, JobId job) {
        return () -> {
            InstanceName instance = job.application().instance();
            Optional<Version> compileVersion = change.revision().map(application.revisions()::get).flatMap(ApplicationVersion::compileVersion);
            List<Version> targets = targetsForPolicy(versionStatus,
                                                     systemVersion,
                                                     application.deploymentSpec().instance(instance)
                                                                .map(DeploymentInstanceSpec::upgradePolicy)
                                                                .orElse(UpgradePolicy.defaultPolicy));

            // Prefer fallback with proper confidence.
            for (Version target : targets)
                if (compileVersion.isEmpty() || versionCompatibility.apply(instance).accept(target, compileVersion.get()))
                    return target;

            // Try fallback with any confidence.
            for (VespaVersion target : reversed(versionStatus.deployableVersions()))
                if (compileVersion.isEmpty() || versionCompatibility.apply(instance).accept(target.versionNumber(), compileVersion.get()))
                    return target.versionNumber();

            return compileVersion.orElseThrow(() -> new IllegalArgumentException("no legal platform version exists in this system for compile version " + compileVersion.get()));
        };
    }


    /** The set of jobs that need to run for the given changes to be considered complete. */
    public boolean hasCompleted(InstanceName instance, Change change) {
        DeploymentInstanceSpec spec = application.deploymentSpec().requireInstance(instance);
        if ((spec.concerns(test) || spec.concerns(staging)) && ! spec.concerns(prod)) {
            if (newestTested(instance, run -> run.versions().targetRevision()).map(change::downgrades).orElse(false)) return true;
            if (newestTested(instance, run -> run.versions().targetPlatform()).map(change::downgrades).orElse(false)) return true;
        }

        return jobsToRun(Map.of(instance, change), false).isEmpty();
    }

    /** The set of jobs that need to run for the given changes to be considered complete. */
    private Map<JobId, List<Job>> jobsToRun(Map<InstanceName, Change> changes) {
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
        return allSteps;
    }

    public Optional<Deployment> deploymentFor(JobId job) {
        return Optional.ofNullable(application.require(job.application().instance())
                                              .deployments().get(job.type().zone()));
    }

    private <T extends Comparable<T>> Optional<T> newestTested(InstanceName instance, Function<Run, T> runMapper) {
        Set<CloudName> clouds = Stream.concat(Stream.of(zones.systemZone().getCloudName()),
                                              jobSteps.keySet().stream()
                                                      .filter(job -> job.type().isProduction())
                                                      .map(job -> findCloud(job.type())))
                                      .collect(toSet());
        List<ZoneId> testZones = new ArrayList<>();
        if (application.deploymentSpec().requireInstance(instance).concerns(test))
            for (CloudName cloud: clouds) testZones.add(JobType.systemTest(zones, cloud).zone());
        if (application.deploymentSpec().requireInstance(instance).concerns(staging))
            for (CloudName cloud: clouds) testZones.add(JobType.stagingTest(zones, cloud).zone());

        Map<ZoneId, Optional<T>> newestPerZone = instanceJobs().get(application.id().instance(instance))
                                                               .type(systemTest(null), stagingTest(null))
                                                               .asList().stream().flatMap(jobs -> jobs.runs().values().stream())
                                                               .filter(Run::hasSucceeded)
                                                               .collect(groupingBy(run -> run.id().type().zone(),
                                                                                   mapping(runMapper, Collectors.maxBy(naturalOrder()))));
        return newestPerZone.keySet().containsAll(testZones)
               ? testZones.stream().map(newestPerZone::get)
                          .reduce((o, n) -> o.isEmpty() || n.isEmpty() ? Optional.empty() : n.get().compareTo(o.get()) < 0 ? n : o)
                          .orElse(Optional.empty())
               : Optional.empty();
    }

    /**
     * The change to a revision which all dependencies of the given instance has completed,
     * which does not downgrade any deployments in the instance,
     * which is not already rolling out to the instance, and
     * which causes at least one job to run if deployed to the instance.
     * For the "exclusive" revision upgrade policy it is the oldest such revision; otherwise, it is the latest.
     */
    public Change outstandingChange(InstanceName instance) {
        StepStatus status = instanceSteps().get(instance);
        if (status == null) return Change.empty();
        DeploymentInstanceSpec spec = application.deploymentSpec().requireInstance(instance);
        boolean ascending = next == spec.revisionTarget();
        int cumulativeRisk = 0;
        int nextRisk = 0;
        int skippedCumulativeRisk = 0;
        Instant readySince = now;

        Optional<RevisionId> newestRevision = application.productionDeployments()
                                                         .getOrDefault(instance, List.of()).stream()
                                                         .map(Deployment::revision).max(naturalOrder());
        Change candidate = Change.empty();
        for (ApplicationVersion version : application.revisions().deployable(ascending)) {
            // A revision is only a candidate if it upgrades, and does not downgrade, this instance.
            Change change = Change.of(version.id());
            if (     newestRevision.isPresent() && change.downgrades(newestRevision.get())
                || ! application.require(instance).change().revision().map(change::upgrades).orElse(true)
                ||   hasCompleted(instance, change)) {
                if (ascending) continue;    // Keep looking for the next revision which is an upgrade, or ...
                else return Change.empty(); // ... if the latest is already complete, there's nothing outstanding.
            }

            // This revision contains something new, so start aggregating the risk score.
            skippedCumulativeRisk += version.risk();
            nextRisk = nextRisk > 0 ? nextRisk : version.risk();
            // If it's not yet ready to roll out, we keep looking.
            Optional<Instant> readyAt = status.dependenciesCompletedAt(Change.of(version.id()), Optional.empty());
            if (readyAt.map(now::isBefore).orElse(true)) continue;

            // It's ready. If looking for the latest, max risk is 0, and we'll return now; otherwise, we _may_ keep on looking for more.
            cumulativeRisk += skippedCumulativeRisk;
            skippedCumulativeRisk = 0;
            nextRisk = 0;
            if (cumulativeRisk >= spec.maxRisk())
                return candidate.equals(Change.empty()) ? change : candidate; // If the first candidate exceeds max risk, we have to accept that.

            // Otherwise, we may note this as a candidate, and keep looking for a newer revision, unless that makes us exceed max risk.
            if (readyAt.get().isBefore(readySince)) readySince = readyAt.get();
            candidate = change;
        }
        // If min risk is ready, or max idle time has passed, we return the candidate. Otherwise, no outstanding change is ready.
        return      instanceJobs(instance).values().stream().allMatch(jobs -> jobs.lastTriggered().isEmpty())
               ||   cumulativeRisk >= spec.minRisk()
               ||   cumulativeRisk + nextRisk > spec.maxRisk()
               || ! now.isBefore(readySince.plus(Duration.ofHours(spec.maxIdleHours())))
               ? candidate : Change.empty();
    }

    /** Earliest instant when job was triggered with given versions, or both system and staging tests were successful. */
    public Optional<Instant> verifiedAt(JobId job, Versions versions) {
        Optional<Instant> triggeredAt = allJobs.get(job)
                                               .flatMap(status -> status.runs().values().stream()
                                                                        .filter(run -> run.versions().equals(versions))
                                                                        .findFirst())
                                               .map(Run::start);
        Optional<Instant> systemTestedAt = testedAt(job.application(), systemTest(job.type()), versions);
        Optional<Instant> stagingTestedAt = testedAt(job.application(), stagingTest(job.type()), versions);
        if (systemTestedAt.isEmpty() || stagingTestedAt.isEmpty()) return triggeredAt;
        Optional<Instant> testedAt = systemTestedAt.get().isAfter(stagingTestedAt.get()) ? systemTestedAt : stagingTestedAt;
        return triggeredAt.isPresent() && triggeredAt.get().isBefore(testedAt.get()) ? triggeredAt : testedAt;
    }

    /** Earliest instant when versions were tested for the given instance */
    private Optional<Instant> testedAt(ApplicationId instance, JobType type, Versions versions) {
        return declaredTest(instance, type).map(__ -> allJobs.instance(instance.instance()))
                                                    .orElse(allJobs)
                                                    .type(type).asList().stream()
                                                    .flatMap(status -> RunList.from(status)
                                                                              .on(versions)
                                                                              .matching(run -> run.id().type().zone().equals(type.zone()))
                                                                              .matching(Run::hasSucceeded)
                                                                              .asList().stream()
                                                                              .map(Run::start))
                                                    .min(naturalOrder());
    }

    private Map<JobId, List<Job>> productionJobs(InstanceName instance, Change change, boolean assumeUpgradesSucceed) {
        Map<JobId, List<Job>> jobs = new LinkedHashMap<>();
        jobSteps.forEach((job, step) -> {
            if ( ! job.application().instance().equals(instance) || ! job.type().isProduction())
                return;

            // Signal strict completion criterion by depending on job itself.
            if (step.completedAt(change, Optional.of(job)).isPresent())
                return;

            // When computing eager test jobs for outstanding changes, assume current change completes successfully.
            Optional<Deployment> deployment = deploymentFor(job);
            Optional<Version> existingPlatform = deployment.map(Deployment::version);
            Optional<RevisionId> existingRevision = deployment.map(Deployment::revision);
            boolean deployingCompatibilityChange =    areIncompatible(existingPlatform, change.revision(), job)
                                                   || areIncompatible(change.platform(), existingRevision, job);
            if (assumeUpgradesSucceed) {
                if (deployingCompatibilityChange) // No eager tests for this.
                    return;

                Change currentChange = application.require(instance).change();
                Versions target = Versions.from(currentChange, application, deployment, fallbackPlatform(currentChange, job));
                existingPlatform = Optional.of(target.targetPlatform());
                existingRevision = Optional.of(target.targetRevision());
            }
            List<Job> toRun = new ArrayList<>();
            List<Change> changes =    deployingCompatibilityChange
                                   || allJobs.get(job).flatMap(status -> status.lastCompleted()).isEmpty()
                                      ? List.of(change)
                                      : changes(job, step, change);
            for (Change partial : changes) {
                Job jobToRun = new Job(job.type(),
                                       Versions.from(partial, application, existingPlatform, existingRevision, fallbackPlatform(partial, job)),
                                       step.readyAt(partial, Optional.of(job)),
                                       partial);
                toRun.add(jobToRun);
                // Assume first partial change is applied before the second.
                existingPlatform = Optional.of(jobToRun.versions.targetPlatform());
                existingRevision = Optional.of(jobToRun.versions.targetRevision());
            }
            jobs.put(job, toRun);
        });
        return jobs;
    }

    private boolean areIncompatible(Optional<Version> platform, Optional<RevisionId> revision, JobId job) {
        Optional<Version> compileVersion = revision.map(application.revisions()::get)
                                                   .flatMap(ApplicationVersion::compileVersion);
        return    platform.isPresent()
               && compileVersion.isPresent()
               && versionCompatibility.apply(job.application().instance()).refuse(platform.get(), compileVersion.get());
    }

    /** Changes to deploy with the given job, possibly split in two steps. */
    private List<Change> changes(JobId job, StepStatus step, Change change) {
        if (change.platform().isEmpty() || change.revision().isEmpty() || change.isPinned())
            return List.of(change);

        if (   step.completedAt(change.withoutApplication(), Optional.of(job)).isPresent()
            || step.completedAt(change.withoutPlatform(), Optional.of(job)).isPresent())
            return List.of(change);

        // For a dual change, where both targets remain, we determine what to run by looking at when the two parts became ready:
        // for deployments, we look at dependencies; for production tests, this may be overridden by what is already deployed.
        JobId deployment = new JobId(job.application(), JobType.deploymentTo(job.type().zone()));
        UpgradeRollout rollout = application.deploymentSpec().requireInstance(job.application().instance()).upgradeRollout();
        if (job.type().isTest()) {
            Optional<Instant> platformDeployedAt = jobSteps.get(deployment).completedAt(change.withoutApplication(), Optional.of(deployment));
            Optional<Instant> revisionDeployedAt = jobSteps.get(deployment).completedAt(change.withoutPlatform(), Optional.of(deployment));

            // If only the revision has deployed, then we expect to test that first.
            if (platformDeployedAt.isEmpty() && revisionDeployedAt.isPresent()) return List.of(change.withoutPlatform(), change);

            // If only the upgrade has deployed, then we expect to test that first, with one exception:
            // The revision has caught up to the upgrade at the deployment job; and either
            // the upgrade is failing between deployment and here, or
            // the specified rollout is leading or simultaneous; and
            // the revision is now blocked by waiting for the production test to verify the upgrade.
            // In this case we must abandon the production test on the pure upgrade, so the revision can be deployed.
            if (platformDeployedAt.isPresent() && revisionDeployedAt.isEmpty()) {
                if (jobSteps.get(deployment).readyAt(change, Optional.of(deployment))
                            .map(ready -> ! now.isBefore(ready)).orElse(false)) {
                    return switch (rollout) {
                        // If separate rollout, this test should keep blocking the revision, unless there are failures.
                        case separate -> hasFailures(jobSteps.get(deployment), jobSteps.get(job)) ? List.of(change) : List.of(change.withoutApplication(), change);
                        // If leading rollout, this test should now expect the two changes to fuse and roll together.
                        case leading -> List.of(change);
                        // If simultaneous rollout, this test should now expect the revision to run ahead.
                        case simultaneous -> List.of(change.withoutPlatform(), change);
                    };
                }
                return List.of(change.withoutApplication(), change);
            }
            // If neither is deployed, then neither is ready, and we assume the same order of changes as for the deployment job.
            if (platformDeployedAt.isEmpty())
                return changes(deployment, jobSteps.get(deployment), change);

            // If both are deployed, then we need to follow normal logic for what is ready.
        }

        Optional<Instant> platformReadyAt = step.dependenciesCompletedAt(change.withoutApplication(), Optional.of(job));
        Optional<Instant> revisionReadyAt = step.dependenciesCompletedAt(change.withoutPlatform(), Optional.of(job));

        // If neither change is ready, we guess based on the specified rollout.
        if (platformReadyAt.isEmpty() && revisionReadyAt.isEmpty()) {
            return switch (rollout) {
                case separate -> List.of(change.withoutApplication(), change);  // Platform should stay ahead.
                case leading -> List.of(change);                                // They should eventually join.
                case simultaneous -> List.of(change.withoutPlatform(), change); // Revision should get ahead.
            };
        }

        // If only the revision is ready, we run that first.
        if (platformReadyAt.isEmpty()) return List.of(change.withoutPlatform(), change);

        // If only the platform is ready, we run that first.
        if (revisionReadyAt.isEmpty()) return List.of(change.withoutApplication(), change);

        // Both changes are ready for this step, and we look to the specified rollout to decide.
        boolean platformReadyFirst = platformReadyAt.get().isBefore(revisionReadyAt.get());
        boolean revisionReadyFirst = revisionReadyAt.get().isBefore(platformReadyAt.get());
        boolean failingUpgradeOnlyTests = ! jobs().type(systemTest(job.type()), stagingTest(job.type()))
                                                  .failingHardOn(Versions.from(change.withoutApplication(), application, deploymentFor(job), () -> systemVersion))
                                                  .isEmpty();
        return switch (rollout) {
            case separate ->      // Let whichever change rolled out first, keep rolling first, unless upgrade alone is failing.
                    (platformReadyFirst || platformReadyAt.get().equals(Instant.EPOCH)) // Assume platform was first if no jobs have run yet.
                    ? step.job().flatMap(jobs()::get).flatMap(JobStatus::firstFailing).isPresent() || failingUpgradeOnlyTests
                      ? List.of(change)                                 // Platform was first, but is failing.
                      : List.of(change.withoutApplication(), change)    // Platform was first, and is OK.
                    : revisionReadyFirst
                      ? List.of(change.withoutPlatform(), change)       // Revision was first.
                      : List.of(change);                                // Both ready at the same time, probably due to earlier failure.
            case leading ->      // When one change catches up, they fuse and continue together.
                    List.of(change);
            case simultaneous -> // Revisions are allowed to run ahead, but the job where it caught up should have both changes.
                    platformReadyFirst ? List.of(change) : List.of(change.withoutPlatform(), change);
        };
    }

    /** The test jobs that need to run prior to the given production deployment jobs. */
    public Map<JobId, List<Job>> testJobs(Map<JobId, List<Job>> jobs) {
        Map<JobId, List<Job>> testJobs = new LinkedHashMap<>();
        // First, look for a declared test in the instance of each production job.
        jobs.forEach((job, versionsList) -> {
            for (JobType testType : List.of(systemTest(job.type()), stagingTest(job.type()))) {
                if (job.type().isProduction() && job.type().isDeployment()) {
                    declaredTest(job.application(), testType).ifPresent(testJob -> {
                        for (Job productionJob : versionsList)
                            if (allJobs.successOn(testType, productionJob.versions())
                                       .instance(testJob.application().instance())
                                       .asList().isEmpty())
                                testJobs.merge(testJob, List.of(new Job(testJob.type(),
                                                                        productionJob.versions(),
                                                                        jobSteps().get(testJob).readyAt(productionJob.change),
                                                                        productionJob.change)),
                                               DeploymentStatus::union);
                    });
                }
            }
        });
        // If no declared test in the right instance was triggered, pick one from a different instance.
        jobs.forEach((job, versionsList) -> {
            for (JobType testType : List.of(systemTest(job.type()), stagingTest(job.type()))) {
                for (Job productionJob : versionsList)
                    if (   job.type().isProduction() && job.type().isDeployment()
                        && allJobs.successOn(testType, productionJob.versions()).asList().isEmpty()
                        && testJobs.keySet().stream()
                                   .noneMatch(test ->    test.type().equals(testType) && test.type().zone().equals(testType.zone())
                                                      && testJobs.get(test).stream().anyMatch(testJob -> test.type().isSystemTest() ? testJob.versions().targetsMatch(productionJob.versions())
                                                                                                                                    : testJob.versions().equals(productionJob.versions())))) {
                        JobId testJob = firstDeclaredOrElseImplicitTest(testType);
                        testJobs.merge(testJob,
                                       List.of(new Job(testJob.type(),
                                                       productionJob.versions(),
                                                       jobSteps.get(testJob).readyAt(productionJob.change),
                                                       productionJob.change)),
                                       DeploymentStatus::union);
                    }
            }
        });
        return Collections.unmodifiableMap(testJobs);
    }

    private CloudName findCloud(JobType job) {
        return zones.zones().all().get(job.zone()).map(ZoneApi::getCloudName).orElse(zones.systemZone().getCloudName());
    }

    private JobId firstDeclaredOrElseImplicitTest(JobType testJob) {
        return application.deploymentSpec().instanceNames().stream()
                          .map(name -> new JobId(application.id().instance(name), testJob))
                          .filter(jobSteps::containsKey)
                          .min(comparing(id -> ! jobSteps.get(id).isDeclared())).orElseThrow();
    }

    /** JobId of any declared test of the given type, for the given instance. */
    private Optional<JobId> declaredTest(ApplicationId instanceId, JobType testJob) {
        JobId jobId = new JobId(instanceId, testJob);
        return jobSteps.containsKey(jobId) && jobSteps.get(jobId).isDeclared() ? Optional.of(jobId) : Optional.empty();
    }

    /** A DAG of the dependencies between the primitive steps in the spec, with iteration order equal to declaration order. */
    private Map<JobId, StepStatus> jobDependencies(DeploymentSpec spec, List<StepStatus> allSteps, Function<JobId, JobStatus> jobs) {
        if (DeploymentSpec.empty.equals(spec))
            return Map.of();

        Map<JobId, StepStatus> dependencies = new LinkedHashMap<>();
        List<StepStatus> previous = List.of();
        for (DeploymentSpec.Step step : spec.steps())
            previous = fillStep(dependencies, allSteps, step, previous, null, jobs,
                                instanceWithImplicitTest(test, spec),
                                instanceWithImplicitTest(staging, spec));

        return Collections.unmodifiableMap(dependencies);
    }

    private static InstanceName instanceWithImplicitTest(Environment environment, DeploymentSpec spec) {
        InstanceName first = null;
        for (DeploymentInstanceSpec step : spec.instances()) {
            if (step.concerns(environment)) return null;
            first = first != null ? first : step.name();
        }
        return first;
    }

    /** Adds the primitive steps contained in the given step, which depend on the given previous primitives, to the dependency graph. */
    private List<StepStatus> fillStep(Map<JobId, StepStatus> dependencies, List<StepStatus> allSteps, DeploymentSpec.Step step,
                                      List<StepStatus> previous, InstanceName instance, Function<JobId, JobStatus> jobs,
                                      InstanceName implicitSystemTest, InstanceName implicitStagingTest) {
        if (step.steps().isEmpty() && ! (step instanceof DeploymentInstanceSpec)) {
            if (instance == null)
                return previous; // Ignore test and staging outside all instances.

            if ( ! step.delay().isZero()) {
                StepStatus stepStatus = new DelayStatus((DeploymentSpec.Delay) step, previous, instance);
                allSteps.add(stepStatus);
                return List.of(stepStatus);
            }

            JobType jobType;
            JobId jobId;
            StepStatus stepStatus;
            if (step.concerns(test) || step.concerns(staging)) {
                jobType = step.concerns(test) ? systemTest(null) : stagingTest(null);
                jobId = new JobId(application.id().instance(instance), jobType);
                stepStatus = JobStepStatus.ofTestDeployment((DeclaredZone) step, List.of(), this, jobs.apply(jobId), true);
                previous = new ArrayList<>(previous);
                previous.add(stepStatus);
            }
            else if (step.isTest()) {
                jobType = JobType.test(((DeclaredTest) step).region());
                jobId = new JobId(application.id().instance(instance), jobType);
                stepStatus = JobStepStatus.ofProductionTest((DeclaredTest) step, previous, this, jobs.apply(jobId));
                previous = List.of(stepStatus);
            }
            else if (step.concerns(prod)) {
                jobType = JobType.prod(((DeclaredZone) step).region().get());
                jobId = new JobId(application.id().instance(instance), jobType);
                stepStatus = JobStepStatus.ofProductionDeployment((DeclaredZone) step, previous, this, jobs.apply(jobId));
                previous = List.of(stepStatus);
            }
            else return previous; // Empty container steps end up here, and are simply ignored.
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
            if (instance.equals(implicitSystemTest)) {
                JobId job = new JobId(application.id().instance(instance), systemTest(null));
                JobStepStatus testStatus = JobStepStatus.ofTestDeployment(new DeclaredZone(test), List.of(),
                                                                          this, jobs.apply(job), false);
                dependencies.put(job, testStatus);
                allSteps.add(testStatus);
            }
            if (instance.equals(implicitStagingTest)) {
                JobId job = new JobId(application.id().instance(instance), stagingTest(null));
                JobStepStatus testStatus = JobStepStatus.ofTestDeployment(new DeclaredZone(staging), List.of(),
                                                                          this, jobs.apply(job), false);
                dependencies.put(job, testStatus);
                allSteps.add(testStatus);
            }
        }

        if (step.isOrdered()) {
            for (DeploymentSpec.Step nested : step.steps())
                previous = fillStep(dependencies, allSteps, nested, previous, instance, jobs, implicitSystemTest, implicitStagingTest);

            return previous;
        }

        List<StepStatus> parallel = new ArrayList<>();
        for (DeploymentSpec.Step nested : step.steps())
            parallel.addAll(fillStep(dependencies, allSteps, nested, previous, instance, jobs, implicitSystemTest, implicitStagingTest));

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
        private final List<StepStatus> dependencies; // All direct dependencies of this step.
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
                                            coolingDownUntil(change, dependent))
                                        .flatMap(Optional::stream)
                                        .reduce(ready, maxBy(naturalOrder())));
        }

        /** The time at which all dependencies completed on the given change and / or versions. */
        Optional<Instant> dependenciesCompletedAt(Change change, Optional<JobId> dependent) {
            Instant latest = Instant.EPOCH;
            for (StepStatus step : dependencies) {
                Optional<Instant> completedAt = step.completedAt(change, dependent);
                if (completedAt.isEmpty()) return Optional.empty();
                latest = latest.isBefore(completedAt.get()) ? completedAt.get() : latest;
            }
            return Optional.of(latest);
        }

        /** The time until which this step is blocked by a change blocker. */
        public Optional<Instant> blockedUntil(Change change) { return Optional.empty(); }

        /** The time until which this step is paused by user intervention. */
        public Optional<Instant> pausedUntil() { return Optional.empty(); }

        /** The time until which this step is cooling down, due to consecutive failures. */
        public Optional<Instant> coolingDownUntil(Change change, Optional<JobId> dependent) { return Optional.empty(); }

        /** Whether this step is declared in the deployment spec, or is an implicit step. */
        public boolean isDeclared() { return true; }

    }


    private static class DelayStatus extends StepStatus {

        private DelayStatus(DeploymentSpec.Delay step, List<StepStatus> dependencies, InstanceName instance) {
            super(StepType.delay, step, dependencies, instance);
        }

        @Override
        Optional<Instant> completedAt(Change change, Optional<JobId> dependent) {
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

        /** The time at which this step is ready to run the specified change and / or versions. */
        @Override
        public Optional<Instant> readyAt(Change change) {
            return status.jobSteps.keySet().stream()
                                  .filter(job -> job.type().isProduction() && job.application().instance().equals(instance.name()))
                                  .map(job -> super.readyAt(change, Optional.of(job)))
                    .reduce((o, n) -> o.isEmpty() || n.isEmpty() ? Optional.empty() : n.get().isBefore(o.get()) ? n : o)
                    .orElseGet(() -> super.readyAt(change, Optional.empty()));
        }

        /**
         * Time of completion of its dependencies, if all parts of the given change are contained in the change
         * for this instance, or if no more jobs should run for this instance for the given change.
         */
        @Override
        Optional<Instant> completedAt(Change change, Optional<JobId> dependent) {
            return    (   (change.platform().isEmpty() || change.platform().equals(instance.change().platform()))
                       && (change.revision().isEmpty() || change.revision().equals(instance.change().revision()))
                   || step().steps().stream().noneMatch(step -> step.concerns(prod)))
                      ? dependenciesCompletedAt(change, dependent).or(() -> Optional.of(Instant.EPOCH).filter(__ -> change.hasTargets()))
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
                               || change.revision().isPresent() && blocker.blocksRevisions())) {
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
        public Optional<Instant> coolingDownUntil(Change change, Optional<JobId> dependent) {
            if (job.lastTriggered().isEmpty()) return Optional.empty();
            if (job.lastCompleted().isEmpty()) return Optional.empty();
            if (job.firstFailing().isEmpty() || ! job.firstFailing().get().hasEnded()) return Optional.empty();
            Versions lastVersions = job.lastCompleted().get().versions();
            Versions toRun = Versions.from(change, status.application, dependent.flatMap(status::deploymentFor), status.fallbackPlatform(change, job.id()));
            if ( ! toRun.targetsMatch(lastVersions)) return Optional.empty();
            if (     job.id().type().environment().isTest()
                && ! dependent.map(JobId::type).map(status::findCloud).map(List.of(CloudName.AWS, CloudName.GCP)::contains).orElse(true)
                &&   job.isNodeAllocationFailure()) return Optional.empty();

            if (job.lastStatus().get() == invalidApplication) return Optional.of(status.now.plus(Duration.ofDays(36524))); // 100 years
            Instant firstFailing = job.firstFailing().get().end().get();
            Instant lastCompleted = job.lastCompleted().get().end().get();

            return firstFailing.equals(lastCompleted) ? Optional.of(lastCompleted)
                                                      : Optional.of(lastCompleted.plus(Duration.ofMinutes(10))
                                                                                 .plus(Duration.between(firstFailing, lastCompleted)
                                                                                               .dividedBy(2)))
                    .filter(status.now::isBefore);
        }

        private static JobStepStatus ofProductionDeployment(DeclaredZone step, List<StepStatus> dependencies,
                                                            DeploymentStatus status, JobStatus job) {
            ZoneId zone = ZoneId.from(step.environment(), step.region().get());
            Optional<Deployment> existingDeployment = Optional.ofNullable(status.application().require(job.id().application().instance())
                                                                                .deployments().get(zone));

            return new JobStepStatus(StepType.deployment, step, dependencies, job, status) {

                @Override
                public Optional<Instant> readyAt(Change change, Optional<JobId> dependent) {
                    Optional<Instant> readyAt = super.readyAt(change, dependent);
                    Optional<Instant> testedAt = status.verifiedAt(job.id(), Versions.from(change, status.application, existingDeployment, status.fallbackPlatform(change, job.id())));
                    if (readyAt.isEmpty() || testedAt.isEmpty()) return Optional.empty();
                    return readyAt.get().isAfter(testedAt.get()) ? readyAt : testedAt;
                }

                /** Complete if deployment is on pinned version, and last successful deployment, or if given versions is strictly a downgrade, and this isn't forced by a pin. */
                @Override
                Optional<Instant> completedAt(Change change, Optional<JobId> dependent) {
                    if (     change.isPinned()
                        &&   change.platform().isPresent()
                        && ! existingDeployment.map(Deployment::version).equals(change.platform()))
                        return Optional.empty();

                    if (     change.revision().isPresent()
                        && ! existingDeployment.map(Deployment::revision).equals(change.revision())
                        &&   dependent.equals(job())) // Job should (re-)run in this case, but other dependents need not wait.
                        return Optional.empty();

                    Change fullChange = status.application().require(job.id().application().instance()).change();
                    if (existingDeployment.map(deployment ->    ! (change.upgrades(deployment.version()) || change.upgrades(deployment.revision()))
                                                             &&   (fullChange.downgrades(deployment.version()) || fullChange.downgrades(deployment.revision())))
                                          .orElse(false))
                        return job.lastCompleted().flatMap(Run::end);

                    Optional<Instant> end = Optional.empty();
                    for (Run run : job.runs().descendingMap().values()) {
                        if (run.versions().targetsMatch(change)) {
                            if (run.hasSucceeded()) end = run.end();
                        }
                        else if (dependent.equals(job())) // If strict completion, consider only last time this change was deployed.
                            break;
                    }
                    return end;
                }
            };
        }

        private static JobStepStatus ofProductionTest(DeclaredTest step, List<StepStatus> dependencies,
                                                      DeploymentStatus status, JobStatus job) {
            JobId prodId = new JobId(job.id().application(), JobType.deploymentTo(job.id().type().zone()));
            return new JobStepStatus(StepType.test, step, dependencies, job, status) {
                @Override
                Optional<Instant> readyAt(Change change, Optional<JobId> dependent) {
                    Optional<Instant> readyAt = super.readyAt(change, dependent);
                    Optional<Instant> deployedAt = status.jobSteps().get(prodId).completedAt(change, Optional.of(prodId));
                    if (readyAt.isEmpty() || deployedAt.isEmpty()) return Optional.empty();
                    return readyAt.get().isAfter(deployedAt.get()) ? readyAt : deployedAt;
                }

                @Override
                Optional<Instant> completedAt(Change change, Optional<JobId> dependent) {
                    Optional<Instant> deployedAt = status.jobSteps().get(prodId).completedAt(change, Optional.of(prodId));
                    Versions target = Versions.from(change, status.application(), status.deploymentFor(job.id()), status.fallbackPlatform(change, job.id()));
                    Change applied = Change.empty();
                    if (change.platform().isPresent())
                        applied = applied.with(target.targetPlatform());
                    if (change.revision().isPresent())
                        applied = applied.with(target.targetRevision());
                    Change relevant = applied;

                    return (dependent.equals(job()) ? job.lastTriggered().filter(run -> deployedAt.map(at -> ! run.start().isBefore(at)).orElse(false)).stream()
                                                    : job.runs().values().stream())
                            .filter(Run::hasSucceeded)
                            .filter(run -> run.versions().targetsMatch(relevant))
                            .flatMap(run -> run.end().stream()).findFirst();
                }
            };
        }

        private static JobStepStatus ofTestDeployment(DeclaredZone step, List<StepStatus> dependencies,
                                                      DeploymentStatus status, JobStatus job, boolean declared) {
            return new JobStepStatus(StepType.test, step, dependencies, job, status) {
                @Override
                Optional<Instant> completedAt(Change change, Optional<JobId> dependent) {
                    Optional<ZoneId> requiredTestZone = dependent.map(dep -> job.id().type().isSystemTest() ? status.systemTest(dep.type()).zone()
                                                                                                            : status.stagingTest(dep.type()).zone());
                    return RunList.from(job)
                                  .matching(run -> dependent.flatMap(status::deploymentFor)
                                                            .map(deployment -> run.versions().targetsMatch(Versions.from(change,
                                                                                                                         status.application,
                                                                                                                         Optional.of(deployment),
                                                                                                                         status.fallbackPlatform(change, dependent.get()))))
                                                            .orElseGet(() ->    (change.platform().isEmpty() || change.platform().get().equals(run.versions().targetPlatform()))
                                                                             && (change.revision().isEmpty() || change.revision().get().equals(run.versions().targetRevision()))))
                                  .matching(Run::hasSucceeded)
                                  .matching(run -> requiredTestZone.isEmpty() || requiredTestZone.get().equals(run.id().type().zone()))
                                  .asList().stream()
                                  .map(run -> run.end().get())
                                  .max(naturalOrder());
                }

                @Override
                public boolean isDeclared() { return declared; }
            };
        }

    }

    public static class Job {

        private final JobType type;
        private final Versions versions;
        private final Optional<Instant> readyAt;
        private final Change change;

        public Job(JobType type, Versions versions, Optional<Instant> readyAt, Change change) {
            this.type = type;
            this.versions = type.isSystemTest() ? versions.withoutSources() : versions;
            this.readyAt = readyAt;
            this.change = change;
        }

        public JobType type() {
            return type;
        }

        public Versions versions() {
            return versions;
        }

        public Optional<Instant> readyAt() {
            return readyAt;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Job job = (Job) o;
            return type.zone().equals(job.type.zone()) && versions.equals(job.versions) && readyAt.equals(job.readyAt) && change.equals(job.change);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type.zone(), versions, readyAt, change);
        }

        @Override
        public String toString() {
            return change + " with versions " + versions + ", ready at " + readyAt;
        }

    }

}
