// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller;

import com.yahoo.component.Version;
import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.application.api.ValidationOverrides;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.vespa.curator.Lock;
import com.yahoo.vespa.hosted.controller.api.integration.MetricsService;
import com.yahoo.vespa.hosted.controller.api.integration.MetricsService.ApplicationMetrics;
import com.yahoo.vespa.hosted.controller.api.integration.organization.IssueId;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.application.ApplicationRotation;
import com.yahoo.vespa.hosted.controller.application.ApplicationVersion;
import com.yahoo.vespa.hosted.controller.application.Change;
import com.yahoo.vespa.hosted.controller.application.ClusterInfo;
import com.yahoo.vespa.hosted.controller.application.ClusterUtilization;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.application.DeploymentMetrics;
import com.yahoo.vespa.hosted.controller.application.JobStatus;
import com.yahoo.vespa.hosted.controller.rotation.RotationId;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * An application that has been locked for modification. Provides methods for modifying an application's fields.
 *
 * @author mpolden
 * @author jonmv
 */
public class LockedApplication {

    private final Lock lock;
    private final ApplicationId id;
    private final DeploymentSpec deploymentSpec;
    private final ValidationOverrides validationOverrides;
    private final Map<ZoneId, Deployment> deployments;
    private final DeploymentJobs deploymentJobs;
    private final Change change;
    private final Change outstandingChange;
    private final Optional<IssueId> ownershipIssueId;
    private final ApplicationMetrics metrics;
    private final Optional<RotationId> rotation;

    /**
     * Used to create a locked application
     *
     * @param application The application to lock.
     * @param lock The lock for the application.
     */
    LockedApplication(Application application, Lock lock) {
        this(Objects.requireNonNull(lock, "lock cannot be null"), application.id(),
             application.deploymentSpec(), application.validationOverrides(),
             application.deployments(),
             application.deploymentJobs(), application.change(), application.outstandingChange(),
             application.ownershipIssueId(), application.metrics(),
             application.rotation().map(ApplicationRotation::id));
    }

    private LockedApplication(Lock lock, ApplicationId id,
                              DeploymentSpec deploymentSpec, ValidationOverrides validationOverrides,
                              Map<ZoneId, Deployment> deployments, DeploymentJobs deploymentJobs, Change change,
                              Change outstandingChange, Optional<IssueId> ownershipIssueId, ApplicationMetrics metrics,
                              Optional<RotationId> rotation) {
        this.lock = lock;
        this.id = id;
        this.deploymentSpec = deploymentSpec;
        this.validationOverrides = validationOverrides;
        this.deployments = deployments;
        this.deploymentJobs = deploymentJobs;
        this.change = change;
        this.outstandingChange = outstandingChange;
        this.ownershipIssueId = ownershipIssueId;
        this.metrics = metrics;
        this.rotation = rotation;
    }

    /** Returns a read-only copy of this */
    public Application get() {
        return new Application(id, deploymentSpec, validationOverrides, deployments, deploymentJobs, change,
                               outstandingChange, ownershipIssueId, metrics, rotation);
    }

    public LockedApplication withProjectId(OptionalLong projectId) {
        return new LockedApplication(lock, id, deploymentSpec, validationOverrides, deployments,
                                     deploymentJobs.withProjectId(projectId), change, outstandingChange,
                                     ownershipIssueId, metrics, rotation);
    }

    public LockedApplication withDeploymentIssueId(IssueId issueId) {
        return new LockedApplication(lock, id, deploymentSpec, validationOverrides, deployments,
                                     deploymentJobs.with(issueId), change, outstandingChange,
                                     ownershipIssueId, metrics, rotation);
    }

    public LockedApplication withJobCompletion(long projectId, JobType jobType, JobStatus.JobRun completion,
                                               Optional<DeploymentJobs.JobError> jobError) {
        return new LockedApplication(lock, id, deploymentSpec, validationOverrides, deployments,
                                     deploymentJobs.withCompletion(projectId, jobType, completion, jobError),
                                     change, outstandingChange, ownershipIssueId, metrics, rotation);
    }

    public LockedApplication withJobTriggering(JobType jobType, JobStatus.JobRun job) {
        return new LockedApplication(lock, id, deploymentSpec, validationOverrides, deployments,
                                     deploymentJobs.withTriggering(jobType, job), change, outstandingChange,
                                     ownershipIssueId, metrics, rotation);
    }

    public LockedApplication withNewDeployment(ZoneId zone, ApplicationVersion applicationVersion, Version version,
                                               Instant instant) {
        // Use info from previous deployment if available, otherwise create a new one.
        Deployment previousDeployment = deployments.getOrDefault(zone, new Deployment(zone, applicationVersion,
                                                                                      version, instant));
        Deployment newDeployment = new Deployment(zone, applicationVersion, version, instant,
                                                  previousDeployment.clusterUtils(),
                                                  previousDeployment.clusterInfo(),
                                                  previousDeployment.metrics(),
                                                  previousDeployment.activity());
        return with(newDeployment);
    }

    public LockedApplication withClusterUtilization(ZoneId zone, Map<ClusterSpec.Id, ClusterUtilization> clusterUtilization) {
        Deployment deployment = deployments.get(zone);
        if (deployment == null) return this;    // No longer deployed in this zone.
        return with(deployment.withClusterUtils(clusterUtilization));
    }

    public LockedApplication withClusterInfo(ZoneId zone, Map<ClusterSpec.Id, ClusterInfo> clusterInfo) {
        Deployment deployment = deployments.get(zone);
        if (deployment == null) return this;    // No longer deployed in this zone.
        return with(deployment.withClusterInfo(clusterInfo));

    }

    public LockedApplication recordActivityAt(Instant instant, ZoneId zone) {
        Deployment deployment = deployments.get(zone);
        if (deployment == null) return this;
        return with(deployment.recordActivityAt(instant));
    }

    public LockedApplication with(ZoneId zone, DeploymentMetrics deploymentMetrics) {
        Deployment deployment = deployments.get(zone);
        if (deployment == null) return this;    // No longer deployed in this zone.
        return with(deployment.withMetrics(deploymentMetrics));
    }

    public LockedApplication withoutDeploymentIn(ZoneId zone) {
        Map<ZoneId, Deployment> deployments = new LinkedHashMap<>(this.deployments);
        deployments.remove(zone);
        return with(deployments);
    }

    public LockedApplication withoutDeploymentJob(JobType jobType) {
        return new LockedApplication(lock, id, deploymentSpec, validationOverrides, deployments,
                                     deploymentJobs.without(jobType), change, outstandingChange,
                                     ownershipIssueId, metrics, rotation);
    }

    public LockedApplication with(DeploymentSpec deploymentSpec) {
        return new LockedApplication(lock, id, deploymentSpec, validationOverrides, deployments,
                                     deploymentJobs, change, outstandingChange,
                                     ownershipIssueId, metrics, rotation);
    }

    public LockedApplication with(ValidationOverrides validationOverrides) {
        return new LockedApplication(lock, id, deploymentSpec, validationOverrides, deployments,
                                     deploymentJobs, change, outstandingChange,
                                     ownershipIssueId, metrics, rotation);
    }

    public LockedApplication withChange(Change change) {
        return new LockedApplication(lock, id, deploymentSpec, validationOverrides, deployments,
                                     deploymentJobs, change, outstandingChange,
                                     ownershipIssueId, metrics, rotation);
    }

    public LockedApplication withOutstandingChange(Change outstandingChange) {
        return new LockedApplication(lock, id, deploymentSpec, validationOverrides, deployments,
                                     deploymentJobs, change, outstandingChange,
                                     ownershipIssueId, metrics, rotation);
    }

    public LockedApplication withOwnershipIssueId(IssueId issueId) {
        return new LockedApplication(lock, id, deploymentSpec, validationOverrides, deployments,
                                     deploymentJobs, change, outstandingChange,
                                     Optional.ofNullable(issueId), metrics, rotation);
    }

    public LockedApplication with(MetricsService.ApplicationMetrics metrics) {
        return new LockedApplication(lock, id, deploymentSpec, validationOverrides, deployments,
                                     deploymentJobs, change, outstandingChange,
                                     ownershipIssueId, metrics, rotation);
    }

    public LockedApplication with(RotationId rotation) {
        return new LockedApplication(lock, id, deploymentSpec, validationOverrides, deployments,
                                     deploymentJobs, change, outstandingChange,
                                     ownershipIssueId, metrics, Optional.of(rotation));
    }

    /** Don't expose non-leaf sub-objects. */
    private LockedApplication with(Deployment deployment) {
        Map<ZoneId, Deployment> deployments = new LinkedHashMap<>(this.deployments);
        deployments.put(deployment.zone(), deployment);
        return with(deployments);
    }

    private LockedApplication with(Map<ZoneId, Deployment> deployments) {
        return new LockedApplication(lock, id, deploymentSpec, validationOverrides, deployments,
                                     deploymentJobs, change, outstandingChange,
                                     ownershipIssueId, metrics, rotation);
    }

    @Override
    public String toString() {
        return "application '" + id + "'";
    }

}
