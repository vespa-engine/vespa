// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.config.application.api.DeploymentInstanceSpec;
import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.application.Deployment;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Comparator.comparingInt;
import static java.util.stream.Collectors.collectingAndThen;

/**
 * This class provides helper methods for reading a deployment spec.
 *
 * @author mpolden
 */
public class DeploymentSteps {

    private final DeploymentInstanceSpec spec;
    private final Supplier<SystemName> system;

    public DeploymentSteps(DeploymentInstanceSpec spec, Supplier<SystemName> system) {
        this.spec = Objects.requireNonNull(spec, "spec cannot be null");
        this.system = Objects.requireNonNull(system, "system cannot be null");
    }

    /** Returns jobs for this, in the order they should run */
    public List<JobType> jobs() {
        return Stream.concat(production().isEmpty() ? Stream.of() : Stream.of(JobType.systemTest, JobType.stagingTest),
                             spec.steps().stream().flatMap(step -> toJobs(step).stream()))
                     .distinct()
                     .collect(Collectors.toUnmodifiableList());
    }

    /** Returns job status sorted according to deployment spec */
    public List<JobStatus> sortedJobs(Collection<JobStatus> jobStatus) {
        List<JobType> sortedJobs = jobs();
        return jobStatus.stream()
                        .sorted(comparingInt(job -> sortedJobs.indexOf(job.id().type())))
                        .collect(Collectors.toUnmodifiableList());
    }

    /** Returns deployments sorted according to declared zones */
    public List<Deployment> sortedDeployments(Collection<Deployment> deployments) {
        List<ZoneId> productionZones = spec.zones().stream()
                                           .filter(z -> z.region().isPresent())
                                           .map(z -> ZoneId.from(z.environment(), z.region().get()))
                                           .collect(Collectors.toUnmodifiableList());
        return deployments.stream()
                          .sorted(comparingInt(deployment -> productionZones.indexOf(deployment.zone())))
                          .collect(collectingAndThen(Collectors.toList(), Collections::unmodifiableList));
    }

    /** Resolve jobs from step */
    public List<JobType> toJobs(DeploymentSpec.Step step) {
        return step.zones().stream()
                   .map(this::toJob)
                   .flatMap(Optional::stream)
                   .collect(Collectors.toUnmodifiableList());
    }

    /** Returns test jobs to run for this spec */
    public List<JobType> testJobs() {
        return jobs().stream().filter(JobType::isTest).collect(Collectors.toUnmodifiableList());
    }

    /** Returns declared production jobs in this */
    public List<JobType> productionJobs() {
        return toJobs(production());
    }

    /** Returns declared production steps in this */
    public List<DeploymentSpec.Step> production() {
        return spec.steps().stream()
                   .filter(step -> ! isTest(step))
                   .collect(Collectors.toUnmodifiableList());
    }

    private boolean isTest(DeploymentSpec.Step step) {
        return step.concerns(Environment.test) || step.concerns(Environment.staging);
    }

    /** Resolve job from deployment zone */
    private Optional<JobType> toJob(DeploymentSpec.DeclaredZone zone) {
        return JobType.from(system.get(), zone.environment(), zone.region().orElse(null));
    }

    /** Resolve jobs from steps */
    private List<JobType> toJobs(List<DeploymentSpec.Step> steps) {
        return steps.stream()
                    .flatMap(step -> toJobs(step).stream())
                    .collect(Collectors.toUnmodifiableList());
    }

}
