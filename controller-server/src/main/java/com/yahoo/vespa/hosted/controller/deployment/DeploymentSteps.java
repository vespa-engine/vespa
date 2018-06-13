// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.SystemName;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType;
import com.yahoo.vespa.hosted.controller.application.JobStatus;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static java.util.Collections.singletonList;
import static java.util.Comparator.comparingInt;
import static java.util.stream.Collectors.collectingAndThen;

/**
 * This class provides helper methods for reading a deployment spec.
 *
 * @author mpolden
 */
public class DeploymentSteps {

    private final DeploymentSpec spec;
    private final Supplier<SystemName> system;

    public DeploymentSteps(DeploymentSpec spec, Supplier<SystemName> system) {
        this.spec = Objects.requireNonNull(spec, "spec cannot be null");
        this.system = Objects.requireNonNull(system, "system cannot be null");
    }

    /** Returns jobs for this, in the order they are declared */
    public List<JobType> jobs() {
        return spec.steps().stream()
                   .flatMap(step -> step.zones().stream())
                   .map(this::toJob)
                   .collect(collectingAndThen(Collectors.toList(), Collections::unmodifiableList));
    }

    /** Returns job status sorted according to deployment spec */
    public List<JobStatus> sortBy(Collection<JobStatus> jobStatus) {
        List<DeploymentJobs.JobType> sortedJobs = jobs();
        return jobStatus.stream()
                        .sorted(comparingInt(job -> sortedJobs.indexOf(job.type())))
                        .collect(collectingAndThen(Collectors.toList(), Collections::unmodifiableList));
    }

    /** Returns deployments sorted according to declared zones */
    public List<Deployment> sortBy2(Collection<Deployment> deployments) {
        List<ZoneId> productionZones = spec.zones().stream()
                                           .filter(z -> z.region().isPresent())
                                           .map(z -> ZoneId.from(z.environment(), z.region().get()))
                                           .collect(Collectors.toList());
        return deployments.stream()
                          .sorted(comparingInt(deployment -> productionZones.indexOf(deployment.zone())))
                          .collect(collectingAndThen(Collectors.toList(), Collections::unmodifiableList));
    }

    /** Resolve jobs from step */
    public List<JobType> toJobs(DeploymentSpec.Step step) {
        return step.zones().stream()
                   .map(this::toJob)
                   .collect(collectingAndThen(Collectors.toList(), Collections::unmodifiableList));
    }

    /** Returns test jobs in this */
    public List<JobType> testJobs() {
        return toJobs(test());
    }

    /** Returns production jobs in this */
    public List<JobType> productionJobs() {
        return toJobs(production());
    }

    /** Returns test steps in this */
    public List<DeploymentSpec.Step> test() {
        if (spec.steps().isEmpty()) {
            return singletonList(new DeploymentSpec.DeclaredZone(Environment.test));
        }
        return spec.steps().stream()
                   .filter(step -> step.deploysTo(Environment.test) || step.deploysTo(Environment.staging))
                   .collect(collectingAndThen(Collectors.toList(), Collections::unmodifiableList));
    }

    /** Returns production steps in this */
    public List<DeploymentSpec.Step> production() {
        return spec.steps().stream()
                   .filter(step -> step.deploysTo(Environment.prod) || step.zones().isEmpty())
                   .collect(collectingAndThen(Collectors.toList(), Collections::unmodifiableList));
    }

    /** Resolve job from deployment zone */
    private JobType toJob(DeploymentSpec.DeclaredZone zone) {
        return JobType.from(system.get(), zone.environment(), zone.region().orElse(null))
                      .orElseThrow(() -> new IllegalArgumentException("Invalid zone " + zone));
    }

    /** Resolve jobs from steps */
    private List<JobType> toJobs(List<DeploymentSpec.Step> steps) {
        return steps.stream()
                    .flatMap(step -> toJobs(step).stream())
                    .collect(collectingAndThen(Collectors.toList(), Collections::unmodifiableList));
    }

}
