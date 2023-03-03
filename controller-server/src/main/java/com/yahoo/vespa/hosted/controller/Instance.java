// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RevisionId;
import com.yahoo.vespa.hosted.controller.application.AssignedRotation;
import com.yahoo.vespa.hosted.controller.application.Change;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.application.DeploymentActivity;
import com.yahoo.vespa.hosted.controller.application.DeploymentMetrics;
import com.yahoo.vespa.hosted.controller.application.QuotaUsage;
import com.yahoo.vespa.hosted.controller.routing.rotation.RotationStatus;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * An instance of an application.
 *
 * This is immutable.
 *
 * @author bratseth
 */
public class Instance {

    private final ApplicationId id;
    private final Map<ZoneId, Deployment> deployments;
    private final List<AssignedRotation> rotations;
    private final RotationStatus rotationStatus;
    private final Map<JobType, Instant> jobPauses;
    private final Change change;

    /** Creates an empty instance */
    public Instance(ApplicationId id) {
        this(id, Set.of(), Map.of(), List.of(), RotationStatus.EMPTY, Change.empty());
    }

    /** Creates an empty instance*/
    public Instance(ApplicationId id, Collection<Deployment> deployments, Map<JobType, Instant> jobPauses,
                    List<AssignedRotation> rotations, RotationStatus rotationStatus, Change change) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.deployments = Objects.requireNonNull(deployments, "deployments cannot be null").stream()
                                  .collect(Collectors.toUnmodifiableMap(Deployment::zone, Function.identity()));
        this.jobPauses = Map.copyOf(Objects.requireNonNull(jobPauses, "deploymentJobs cannot be null"));
        this.rotations = List.copyOf(Objects.requireNonNull(rotations, "rotations cannot be null"));
        this.rotationStatus = Objects.requireNonNull(rotationStatus, "rotationStatus cannot be null");
        this.change = Objects.requireNonNull(change, "change cannot be null");
    }

    public Instance withNewDeployment(ZoneId zone, RevisionId revision, Version version, Instant instant,
                                      Map<DeploymentMetrics.Warning, Integer> warnings, QuotaUsage quotaUsage, CloudAccount cloudAccount) {
        // Use info from previous deployment if available, otherwise create a new one.
        Deployment previousDeployment = deployments.getOrDefault(zone, new Deployment(zone, cloudAccount, revision,
                                                                                      version, instant,
                                                                                      DeploymentMetrics.none,
                                                                                      DeploymentActivity.none,
                                                                                      QuotaUsage.none,
                                                                                      OptionalDouble.empty()));
        Deployment newDeployment = new Deployment(zone, cloudAccount, revision, version, instant,
                                                  previousDeployment.metrics().with(warnings),
                                                  previousDeployment.activity(),
                                                  quotaUsage,
                                                  previousDeployment.cost());
        return with(newDeployment);
    }

    public Instance withJobPause(JobType jobType, OptionalLong pausedUntil) {
        Map<JobType, Instant> jobPauses = new HashMap<>(this.jobPauses);
        if (pausedUntil.isPresent())
            jobPauses.put(jobType, Instant.ofEpochMilli(pausedUntil.getAsLong()));
        else
            jobPauses.remove(jobType);

        return new Instance(id, deployments.values(), jobPauses, rotations, rotationStatus, change);
    }

    public Instance recordActivityAt(Instant instant, ZoneId zone) {
        Deployment deployment = deployments.get(zone);
        if (deployment == null) return this;
        return with(deployment.recordActivityAt(instant));
    }

    public Instance with(ZoneId zone, DeploymentMetrics deploymentMetrics) {
        Deployment deployment = deployments.get(zone);
        if (deployment == null) return this;    // No longer deployed in this zone.
        return with(deployment.withMetrics(deploymentMetrics));
    }

    public Instance withDeploymentCosts(Map<ZoneId, Double> costByZone) {
        Map<ZoneId, Deployment> deployments = this.deployments.entrySet().stream()
                .map(entry -> Optional.ofNullable(costByZone.get(entry.getKey()))
                        .map(entry.getValue()::withCost)
                        .orElseGet(entry.getValue()::withoutCost))
                .collect(Collectors.toUnmodifiableMap(Deployment::zone, deployment -> deployment));
        return with(deployments);
    }

    public Instance withoutDeploymentIn(ZoneId zone) {
        Map<ZoneId, Deployment> deployments = new LinkedHashMap<>(this.deployments);
        deployments.remove(zone);
        return with(deployments);
    }

    public Instance with(List<AssignedRotation> assignedRotations) {
        return new Instance(id, deployments.values(), jobPauses, assignedRotations, rotationStatus, change);
    }

    public Instance with(RotationStatus rotationStatus) {
        return new Instance(id, deployments.values(), jobPauses, rotations, rotationStatus, change);
    }

    public Instance withChange(Change change) {
        return new Instance(id, deployments.values(), jobPauses, rotations, rotationStatus, change);
    }

    private Instance with(Deployment deployment) {
        Map<ZoneId, Deployment> deployments = new LinkedHashMap<>(this.deployments);
        deployments.put(deployment.zone(), deployment);
        return with(deployments);
    }

    private Instance with(Map<ZoneId, Deployment> deployments) {
        return new Instance(id, deployments.values(), jobPauses, rotations, rotationStatus, change);
    }

    public ApplicationId id() { return id; }

    public InstanceName name() { return id.instance(); }

    /** Returns an immutable map of the current deployments of this */
    public Map<ZoneId, Deployment> deployments() { return deployments; }

    /**
     * Returns an immutable map of the current *production* deployments of this
     * (deployments also includes manually deployed environments)
     */
    public Map<ZoneId, Deployment> productionDeployments() {
        return deployments.values().stream()
                          .filter(deployment -> deployment.zone().environment() == Environment.prod)
                          .collect(Collectors.toUnmodifiableMap(Deployment::zone, Function.identity()));
    }

    /** Returns the instant until which the given job is paused, or empty. */
    public Optional<Instant> jobPause(JobType jobType) {
        return Optional.ofNullable(jobPauses.get(jobType));
    }

    /** Returns the set of instants until which any paused jobs of this instance should remain paused, indexed by job type. */
    public Map<JobType, Instant> jobPauses() {
        return jobPauses;
    }

    /** Returns all rotations assigned to this */
    public List<AssignedRotation> rotations() {
        return rotations;
    }

    /** Returns the status of the global rotation(s) assigned to this */
    public RotationStatus rotationStatus() {
        return rotationStatus;
    }

    /** Returns the currently deploying change for this instance. */
    public Change change() {
        return change;
    }

    /** Returns the total quota usage for this instance, excluding temporary deployments **/
    public QuotaUsage quotaUsage() {
        return deployments.values().stream()
                .filter(d -> !d.zone().environment().isTest()) // Exclude temporary deployments
                .map(Deployment::quota).reduce(QuotaUsage::add).orElse(QuotaUsage.none);
    }

    /** Returns the total quota usage for manual deployments for this instance **/
    public QuotaUsage manualQuotaUsage() {
        return deployments.values().stream()
                .filter(d -> d.zone().environment().isManuallyDeployed())
                .map(Deployment::quota).reduce(QuotaUsage::add).orElse(QuotaUsage.none);
    }

    /** Returns the total quota usage for this instance, excluding one specific deployment (and temporary deployments) */
    public QuotaUsage quotaUsageExcluding(ApplicationId application, ZoneId zone) {
        return deployments.values().stream()
                .filter(d -> !d.zone().environment().isTest()) // Exclude temporary deployments
                .filter(d -> !(application.equals(id) && d.zone().equals(zone)))
                .map(Deployment::quota).reduce(QuotaUsage::add).orElse(QuotaUsage.none);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if ( ! (o instanceof Instance)) return false;

        Instance that = (Instance) o;

        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "application instance '" + id.toFullString() + "'";
    }
}
