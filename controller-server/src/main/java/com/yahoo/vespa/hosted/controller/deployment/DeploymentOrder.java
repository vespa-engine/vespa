// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.provision.SystemName;
import com.yahoo.vespa.hosted.controller.Controller;
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

import static java.util.Comparator.comparingInt;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toList;

/**
 * This class determines the order of deployments according to an application's deployment spec.
 *
 * @author mpolden
 */
public class DeploymentOrder {

    private final Supplier<SystemName> system;

    public DeploymentOrder(Supplier<SystemName> system) {
        Objects.requireNonNull(system, "system may not be null");
        this.system = system;
    }

    /** Returns jobs for given deployment spec, in the order they are declared */
    public List<JobType> jobsFrom(DeploymentSpec deploymentSpec) {
        return deploymentSpec.steps().stream()
                             .flatMap(step -> step.zones().stream())
                             .map(this::toJob)
                             .collect(collectingAndThen(toList(), Collections::unmodifiableList));
    }

    /** Returns job status sorted according to deployment spec */
    public List<JobStatus> sortBy(DeploymentSpec deploymentSpec, Collection<JobStatus> jobStatus) {
        List<DeploymentJobs.JobType> sortedJobs = jobsFrom(deploymentSpec);
        return jobStatus.stream()
                .sorted(comparingInt(job -> sortedJobs.indexOf(job.type())))
                .collect(collectingAndThen(toList(), Collections::unmodifiableList));
    }

    /** Returns deployments sorted according to declared zones */
    public List<Deployment> sortBy(List<DeploymentSpec.DeclaredZone> zones, Collection<Deployment> deployments) {
        List<ZoneId> productionZones = zones.stream()
                .filter(z -> z.region().isPresent())
                .map(z -> ZoneId.from(z.environment(), z.region().get()))
                .collect(toList());
        return deployments.stream()
                .sorted(comparingInt(deployment -> productionZones.indexOf(deployment.zone())))
                .collect(collectingAndThen(toList(), Collections::unmodifiableList));
    }

    /** Resolve job from deployment step */
    public JobType toJob(DeploymentSpec.DeclaredZone zone) {
        return JobType.from(system.get(), zone.environment(), zone.region().orElse(null))
                .orElseThrow(() -> new IllegalArgumentException("Invalid zone " + zone));
    }

}
