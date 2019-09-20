// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller;

import com.yahoo.component.Version;
import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.application.api.ValidationOverrides;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.curator.Lock;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.ApplicationVersion;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.api.integration.organization.IssueId;
import com.yahoo.vespa.hosted.controller.api.integration.organization.User;
import com.yahoo.vespa.hosted.controller.application.AssignedRotation;
import com.yahoo.vespa.hosted.controller.application.Change;
import com.yahoo.vespa.hosted.controller.application.ClusterInfo;
import com.yahoo.vespa.hosted.controller.application.ClusterUtilization;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs;
import com.yahoo.vespa.hosted.controller.application.DeploymentMetrics;
import com.yahoo.vespa.hosted.controller.application.JobStatus;
import com.yahoo.vespa.hosted.controller.metric.ApplicationMetrics;
import com.yahoo.vespa.hosted.controller.rotation.RotationStatus;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

/**
 * An application that has been locked for modification. Provides methods for modifying an application's fields.
 *
 * @author mpolden
 * @author jonmv
 */
public class LockedInstance {

    private final Lock lock;
    private final ApplicationId id;
    private final Instant createdAt;
    private final DeploymentSpec deploymentSpec;
    private final ValidationOverrides validationOverrides;
    private final Map<ZoneId, Deployment> deployments;
    private final DeploymentJobs deploymentJobs;
    private final Change change;
    private final Change outstandingChange;
    private final Optional<IssueId> ownershipIssueId;
    private final Optional<User> owner;
    private final OptionalInt majorVersion;
    private final ApplicationMetrics metrics;
    private final Optional<String> pemDeployKey;
    private final List<AssignedRotation> rotations;
    private final RotationStatus rotationStatus;

    /**
     * Used to create a locked application
     *
     * @param instance The application to lock.
     * @param lock The lock for the application.
     */
    LockedInstance(Instance instance, Lock lock) {
        this(Objects.requireNonNull(lock, "lock cannot be null"), instance.id(), instance.createdAt(),
             instance.deploymentSpec(), instance.validationOverrides(),
             instance.deployments(),
             instance.deploymentJobs(), instance.change(), instance.outstandingChange(),
             instance.ownershipIssueId(), instance.owner(), instance.majorVersion(), instance.metrics(),
             instance.pemDeployKey(), instance.rotations(), instance.rotationStatus());
    }

    private LockedInstance(Lock lock, ApplicationId id, Instant createdAt,
                           DeploymentSpec deploymentSpec, ValidationOverrides validationOverrides,
                           Map<ZoneId, Deployment> deployments, DeploymentJobs deploymentJobs, Change change,
                           Change outstandingChange, Optional<IssueId> ownershipIssueId, Optional<User> owner,
                           OptionalInt majorVersion, ApplicationMetrics metrics, Optional<String> pemDeployKey,
                           List<AssignedRotation> rotations, RotationStatus rotationStatus) {
        this.lock = lock;
        this.id = id;
        this.createdAt = createdAt;
        this.deploymentSpec = deploymentSpec;
        this.validationOverrides = validationOverrides;
        this.deployments = deployments;
        this.deploymentJobs = deploymentJobs;
        this.change = change;
        this.outstandingChange = outstandingChange;
        this.ownershipIssueId = ownershipIssueId;
        this.owner = owner;
        this.majorVersion = majorVersion;
        this.metrics = metrics;
        this.pemDeployKey = pemDeployKey;
        this.rotations = rotations;
        this.rotationStatus = rotationStatus;
    }

    /** Returns a read-only copy of this */
    public Instance get() {
        return new Instance(id, createdAt, deploymentSpec, validationOverrides, deployments, deploymentJobs, change,
                            outstandingChange, ownershipIssueId, owner, majorVersion, metrics, pemDeployKey,
                            rotations, rotationStatus);
    }

    public LockedInstance withJobPause(JobType jobType, OptionalLong pausedUntil) {
        return new LockedInstance(lock, id, createdAt, deploymentSpec, validationOverrides, deployments,
                                  deploymentJobs.withPause(jobType, pausedUntil), change, outstandingChange,
                                  ownershipIssueId, owner, majorVersion, metrics, pemDeployKey,
                                  rotations, rotationStatus);
    }

    public LockedInstance withJobCompletion(long projectId, JobType jobType, JobStatus.JobRun completion,
                                            Optional<DeploymentJobs.JobError> jobError) {
        return new LockedInstance(lock, id, createdAt, deploymentSpec, validationOverrides, deployments,
                                  deploymentJobs.withCompletion(projectId, jobType, completion, jobError),
                                  change, outstandingChange, ownershipIssueId, owner, majorVersion, metrics,
                                  pemDeployKey, rotations, rotationStatus);
    }

    public LockedInstance withJobTriggering(JobType jobType, JobStatus.JobRun job) {
        return new LockedInstance(lock, id, createdAt, deploymentSpec, validationOverrides, deployments,
                                  deploymentJobs.withTriggering(jobType, job), change, outstandingChange,
                                  ownershipIssueId, owner, majorVersion, metrics, pemDeployKey,
                                  rotations, rotationStatus);
    }

    public LockedInstance withNewDeployment(ZoneId zone, ApplicationVersion applicationVersion, Version version,
                                            Instant instant, Map<DeploymentMetrics.Warning, Integer> warnings) {
        // Use info from previous deployment if available, otherwise create a new one.
        Deployment previousDeployment = deployments.getOrDefault(zone, new Deployment(zone, applicationVersion,
                                                                                      version, instant));
        Deployment newDeployment = new Deployment(zone, applicationVersion, version, instant,
                                                  previousDeployment.clusterUtils(),
                                                  previousDeployment.clusterInfo(),
                                                  previousDeployment.metrics().with(warnings),
                                                  previousDeployment.activity());
        return with(newDeployment);
    }

    public LockedInstance withClusterUtilization(ZoneId zone, Map<ClusterSpec.Id, ClusterUtilization> clusterUtilization) {
        Deployment deployment = deployments.get(zone);
        if (deployment == null) return this;    // No longer deployed in this zone.
        return with(deployment.withClusterUtils(clusterUtilization));
    }

    public LockedInstance withClusterInfo(ZoneId zone, Map<ClusterSpec.Id, ClusterInfo> clusterInfo) {
        Deployment deployment = deployments.get(zone);
        if (deployment == null) return this;    // No longer deployed in this zone.
        return with(deployment.withClusterInfo(clusterInfo));

    }

    public LockedInstance recordActivityAt(Instant instant, ZoneId zone) {
        Deployment deployment = deployments.get(zone);
        if (deployment == null) return this;
        return with(deployment.recordActivityAt(instant));
    }

    public LockedInstance with(ZoneId zone, DeploymentMetrics deploymentMetrics) {
        Deployment deployment = deployments.get(zone);
        if (deployment == null) return this;    // No longer deployed in this zone.
        return with(deployment.withMetrics(deploymentMetrics));
    }

    public LockedInstance withoutDeploymentIn(ZoneId zone) {
        Map<ZoneId, Deployment> deployments = new LinkedHashMap<>(this.deployments);
        deployments.remove(zone);
        return with(deployments);
    }

    public LockedInstance withoutDeploymentJob(JobType jobType) {
        return new LockedInstance(lock, id, createdAt, deploymentSpec, validationOverrides, deployments,
                                  deploymentJobs.without(jobType), change, outstandingChange,
                                  ownershipIssueId, owner, majorVersion, metrics, pemDeployKey,
                                  rotations, rotationStatus);
    }

    public LockedInstance withChange(Change change) {
        return new LockedInstance(lock, id, createdAt, deploymentSpec, validationOverrides, deployments,
                                  deploymentJobs, change, outstandingChange, ownershipIssueId, owner, majorVersion,
                                  metrics, pemDeployKey, rotations, rotationStatus);
    }

    public LockedInstance withOutstandingChange(Change outstandingChange) {
        return new LockedInstance(lock, id, createdAt, deploymentSpec, validationOverrides, deployments,
                                  deploymentJobs, change, outstandingChange, ownershipIssueId, owner, majorVersion,
                                  metrics, pemDeployKey, rotations, rotationStatus);
    }

    public LockedInstance withOwnershipIssueId(IssueId issueId) {
        return new LockedInstance(lock, id, createdAt, deploymentSpec, validationOverrides, deployments,
                                  deploymentJobs, change, outstandingChange, Optional.ofNullable(issueId), owner,
                                  majorVersion, metrics, pemDeployKey, rotations, rotationStatus);
    }

    public LockedInstance withOwner(User owner) {
        return new LockedInstance(lock, id, createdAt, deploymentSpec, validationOverrides, deployments,
                                  deploymentJobs, change, outstandingChange, ownershipIssueId,
                                  Optional.ofNullable(owner), majorVersion, metrics, pemDeployKey,
                                  rotations, rotationStatus);
    }

    /** Set a major version for this, or set to null to remove any major version override */
    public LockedInstance withMajorVersion(Integer majorVersion) {
        return new LockedInstance(lock, id, createdAt, deploymentSpec, validationOverrides, deployments,
                                  deploymentJobs, change, outstandingChange, ownershipIssueId, owner,
                                     majorVersion == null ? OptionalInt.empty() : OptionalInt.of(majorVersion),
                                  metrics, pemDeployKey, rotations, rotationStatus);
    }

    public LockedInstance with(ApplicationMetrics metrics) {
        return new LockedInstance(lock, id, createdAt, deploymentSpec, validationOverrides, deployments,
                                  deploymentJobs, change, outstandingChange, ownershipIssueId, owner, majorVersion,
                                  metrics, pemDeployKey, rotations, rotationStatus);
    }

    public LockedInstance withPemDeployKey(String pemDeployKey) {
        return new LockedInstance(lock, id, createdAt, deploymentSpec, validationOverrides, deployments,
                                  deploymentJobs, change, outstandingChange, ownershipIssueId, owner, majorVersion,
                                  metrics, Optional.ofNullable(pemDeployKey), rotations, rotationStatus);
    }

    public LockedInstance with(List<AssignedRotation> assignedRotations) {
        return new LockedInstance(lock, id, createdAt, deploymentSpec, validationOverrides, deployments,
                                  deploymentJobs, change, outstandingChange, ownershipIssueId, owner, majorVersion,
                                  metrics, pemDeployKey, assignedRotations, rotationStatus);
    }

    public LockedInstance with(RotationStatus rotationStatus) {
        return new LockedInstance(lock, id, createdAt, deploymentSpec, validationOverrides, deployments,
                                  deploymentJobs, change, outstandingChange, ownershipIssueId, owner, majorVersion,
                                  metrics, pemDeployKey, rotations, rotationStatus);
    }

    /** Don't expose non-leaf sub-objects. */
    private LockedInstance with(Deployment deployment) {
        Map<ZoneId, Deployment> deployments = new LinkedHashMap<>(this.deployments);
        deployments.put(deployment.zone(), deployment);
        return with(deployments);
    }

    private LockedInstance with(Map<ZoneId, Deployment> deployments) {
        return new LockedInstance(lock, id, createdAt, deploymentSpec, validationOverrides, deployments,
                                  deploymentJobs, change, outstandingChange, ownershipIssueId, owner, majorVersion,
                                  metrics, pemDeployKey, rotations, rotationStatus);
    }

    @Override
    public String toString() {
        return "application '" + id + "'";
    }

}
