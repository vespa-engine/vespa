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
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType;
import com.yahoo.vespa.hosted.controller.application.DeploymentMetrics;
import com.yahoo.vespa.hosted.controller.application.JobStatus;
import com.yahoo.vespa.hosted.controller.rotation.RotationId;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * A combination of an application instance and a lock for that application. Provides methods for updating application
 * fields.
 *
 * @author mpolden
 * @author jvenstad
 */
public class LockedApplication extends Application {

    /**
     * Used to create a locked application
     *
     * @param application The application to lock.
     * @param lock The lock for the application.
     */
    LockedApplication(Application application, @SuppressWarnings("unused") Lock lock) {
        this(new Builder(application));
    }

    private LockedApplication(Builder builder) {
        super(builder.applicationId, builder.deploymentSpec, builder.validationOverrides,
              builder.deployments, builder.deploymentJobs, builder.deploying,
              builder.outstandingChange, builder.ownershipIssueId, builder.metrics, builder.rotation);
    }

    public LockedApplication withProjectId(OptionalLong projectId) {
        return new LockedApplication(new Builder(this).with(deploymentJobs().withProjectId(projectId)));
    }

    public LockedApplication withDeploymentIssueId(IssueId issueId) {
        return new LockedApplication(new Builder(this).with(deploymentJobs().with(issueId)));
    }

    public LockedApplication withJobCompletion(long projectId, JobType jobType, JobStatus.JobRun completion, Optional<DeploymentJobs.JobError> jobError) {
        return new LockedApplication(new Builder(this).with(deploymentJobs().withCompletion(projectId, jobType, completion, jobError))
        );
    }

    public LockedApplication withJobTriggering(JobType jobType, JobStatus.JobRun job) {
        return new LockedApplication(new Builder(this).with(deploymentJobs().withTriggering(jobType, job)));
    }

    public LockedApplication withNewDeployment(ZoneId zone, ApplicationVersion applicationVersion, Version version,
                                               Instant instant) {
        // Use info from previous deployment if available, otherwise create a new one.
        Deployment previousDeployment = deployments().getOrDefault(zone, new Deployment(zone, applicationVersion,
                                                                                        version, instant));
        Deployment newDeployment = new Deployment(zone, applicationVersion, version, instant,
                                                  previousDeployment.clusterUtils(),
                                                  previousDeployment.clusterInfo(),
                                                  previousDeployment.metrics(),
                                                  previousDeployment.activity());
        return with(newDeployment);
    }

    public LockedApplication withClusterUtilization(ZoneId zone, Map<ClusterSpec.Id, ClusterUtilization> clusterUtilization) {
        Deployment deployment = deployments().get(zone);
        if (deployment == null) return this;    // No longer deployed in this zone.
        return with(deployment.withClusterUtils(clusterUtilization));
    }

    public LockedApplication withClusterInfo(ZoneId zone, Map<ClusterSpec.Id, ClusterInfo> clusterInfo) {
        Deployment deployment = deployments().get(zone);
        if (deployment == null) return this;    // No longer deployed in this zone.
        return with(deployment.withClusterInfo(clusterInfo));

    }

    public LockedApplication recordActivityAt(Instant instant, ZoneId zone) {
        Deployment deployment = deployments().get(zone);
        if (deployment == null) return this;
        return with(deployment.recordActivityAt(instant));
    }

    public LockedApplication with(ZoneId zone, DeploymentMetrics deploymentMetrics) {
        Deployment deployment = deployments().get(zone);
        if (deployment == null) return this;    // No longer deployed in this zone.
        return with(deployment.withMetrics(deploymentMetrics));
    }

    public LockedApplication withoutDeploymentIn(ZoneId zone) {
        Map<ZoneId, Deployment> deployments = new LinkedHashMap<>(deployments());
        deployments.remove(zone);
        return new LockedApplication(new Builder(this).with(deployments));
    }

    public LockedApplication withoutDeploymentJob(DeploymentJobs.JobType jobType) {
        return new LockedApplication(new Builder(this).with(deploymentJobs().without(jobType)));
    }

    public LockedApplication with(DeploymentSpec deploymentSpec) {
        return new LockedApplication(new Builder(this).with(deploymentSpec));
    }

    public LockedApplication with(ValidationOverrides validationOverrides) {
        return new LockedApplication(new Builder(this).with(validationOverrides));
    }

    public LockedApplication withChange(Change change) {
        return new LockedApplication(new Builder(this).withChange(change));
    }

    public LockedApplication withOutstandingChange(Change outstandingChange) {
        return new LockedApplication(new Builder(this).withOutstandingChange(outstandingChange));
    }

    public LockedApplication withOwnershipIssueId(IssueId issueId) {
        return new LockedApplication(new Builder(this).withOwnershipIssueId(Optional.ofNullable(issueId)));
    }

    public LockedApplication with(MetricsService.ApplicationMetrics metrics) {
        return new LockedApplication(new Builder(this).with(metrics));
    }

    public LockedApplication with(RotationId rotation) {
        return new LockedApplication(new Builder(this).with(rotation));
    }

    /** Don't expose non-leaf sub-objects. */
    private LockedApplication with(Deployment deployment) {
        Map<ZoneId, Deployment> deployments = new LinkedHashMap<>(deployments());
        deployments.put(deployment.zone(), deployment);
        return new LockedApplication(new Builder(this).with(deployments));
    }

    private static class Builder {

        private final ApplicationId applicationId;
        private DeploymentSpec deploymentSpec;
        private ValidationOverrides validationOverrides;
        private Map<ZoneId, Deployment> deployments;
        private DeploymentJobs deploymentJobs;
        private Change deploying;
        private Change outstandingChange;
        private Optional<IssueId> ownershipIssueId;
        private ApplicationMetrics metrics;
        private Optional<RotationId> rotation;

        private Builder(Application application) {
            this.applicationId = application.id();
            this.deploymentSpec = application.deploymentSpec();
            this.validationOverrides = application.validationOverrides();
            this.deployments = application.deployments();
            this.deploymentJobs = application.deploymentJobs();
            this.deploying = application.change();
            this.outstandingChange = application.outstandingChange();
            this.ownershipIssueId = application.ownershipIssueId();
            this.metrics = application.metrics();
            this.rotation = application.rotation().map(ApplicationRotation::id);
        }

        private Builder with(DeploymentSpec deploymentSpec) {
            this.deploymentSpec = deploymentSpec;
            return this;
        }

        private Builder with(ValidationOverrides validationOverrides) {
            this.validationOverrides = validationOverrides;
            return this;
        }

        private Builder with(Map<ZoneId, Deployment> deployments) {
            this.deployments = deployments;
            return this;
        }

        private Builder with(DeploymentJobs deploymentJobs) {
            this.deploymentJobs = deploymentJobs;
            return this;
        }

        private Builder withChange(Change deploying) {
            this.deploying = deploying;
            return this;
        }

        private Builder withOutstandingChange(Change outstandingChange) {
            this.outstandingChange = outstandingChange;
            return this;
        }

        private Builder withOwnershipIssueId(Optional<IssueId> ownershipIssueId) {
            this.ownershipIssueId = ownershipIssueId;
            return this;
        }

        private Builder with(ApplicationMetrics metrics) {
            this.metrics = metrics;
            return this;
        }

        private Builder with(RotationId rotation) {
            this.rotation = Optional.of(rotation);
            return this;
        }

    }

}
