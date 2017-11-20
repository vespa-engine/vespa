package com.yahoo.vespa.hosted.controller;

import com.yahoo.component.Version;
import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.application.api.ValidationOverrides;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.curator.Lock;
import com.yahoo.vespa.hosted.controller.api.integration.MetricsService;
import com.yahoo.vespa.hosted.controller.api.integration.organization.IssueId;
import com.yahoo.vespa.hosted.controller.application.ApplicationRevision;
import com.yahoo.vespa.hosted.controller.application.Change;
import com.yahoo.vespa.hosted.controller.application.ClusterInfo;
import com.yahoo.vespa.hosted.controller.application.ClusterUtilization;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType;
import com.yahoo.vespa.hosted.controller.application.DeploymentMetrics;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A combination of an application instance and a lock for that application. Provides methods for updating application
 * fields.
 *
 * @author mpolden
 */
public class LockedApplication extends Application {

    private final Lock lock;

    /**
     * LockedApplication should be acquired through ApplicationController and never constructed directly
     *
     * @param application Application instance for which lock has been acquired
     * @param lock Unused, but must be held when constructing this
     */
    LockedApplication(Application application, Lock lock) {
        super(application.id(), application.deploymentSpec(), application.validationOverrides(),
              application.deployments(), application.deploymentJobs(), application.deploying(),
              application.hasOutstandingChange(), application.ownershipIssueId(), application.metrics());
        this.lock = Objects.requireNonNull(lock, "lock cannot be null");
    }

    public LockedApplication withProjectId(long projectId) {
        return new LockedApplication(new Application(id(), deploymentSpec(), validationOverrides(), deployments(),
                                                     deploymentJobs().withProjectId(projectId), deploying(),
                                                     hasOutstandingChange(), ownershipIssueId(), metrics()), lock);
    }

    public LockedApplication with(IssueId issueId) {
        return new LockedApplication(new Application(id(), deploymentSpec(), validationOverrides(), deployments(),
                                                     deploymentJobs().with(issueId), deploying(),
                                                     hasOutstandingChange(), ownershipIssueId(), metrics()), lock);
    }

    public LockedApplication withJobCompletion(DeploymentJobs.JobReport report, Instant notificationTime,
                                               Controller controller) {
        return new LockedApplication(new Application(id(), deploymentSpec(), validationOverrides(),
                                                     deployments(),
                                                     deploymentJobs().withCompletion(report, notificationTime,
                                                                                     controller),
                                                     deploying(), hasOutstandingChange(), ownershipIssueId(), metrics()), lock);
    }

    public LockedApplication withJobTriggering(JobType type, Optional<Change> change,
                                               Instant triggerTime, Version version, Optional<ApplicationRevision> revision, String reason) {
        return new LockedApplication(new Application(id(), deploymentSpec(), validationOverrides(), deployments(),
                                                     deploymentJobs().withTriggering(type,
                                                                                     change,
                                                                                     version,
                                                                                     revision,
                                                                                     reason,
                                                                                     triggerTime),
                                                     deploying(), hasOutstandingChange(), ownershipIssueId(), metrics()), lock);
    }

    /** Don't expose non-leaf sub-objects. */
    private LockedApplication with(Deployment deployment) {
        Map<Zone, Deployment> deployments = new LinkedHashMap<>(deployments());
        deployments.put(deployment.zone(), deployment);
        return new LockedApplication(new Application(id(), deploymentSpec(), validationOverrides(),
                                                     deployments, deploymentJobs(), deploying(),
                                                     hasOutstandingChange(), ownershipIssueId(), metrics()), lock);
    }

    public LockedApplication withNewDeployment(Zone zone, ApplicationRevision revision, Version version, Instant instant) {
        // Use info from previous deployments is available
        Deployment previousDeployment = deployments().getOrDefault(zone, new Deployment(zone, revision, version, instant));
        Deployment newDeployment = new Deployment(zone, revision, version, instant,
                                                  previousDeployment.clusterUtils(),
                                                  previousDeployment.clusterInfo(),
                                                  previousDeployment.metrics());
        return with(newDeployment);
    }

    public LockedApplication withClusterUtilization(Zone zone, Map<ClusterSpec.Id, ClusterUtilization> clusterUtilization) {
        Deployment deployment = deployments().get(zone);
        if (deployment == null) return this;    // No longer deployed in this zone.
        return with(deployment.withClusterUtils(clusterUtilization));
    }

    public LockedApplication withClusterInfo(Zone zone, Map<ClusterSpec.Id, ClusterInfo> clusterInfo) {
        Deployment deployment = deployments().get(zone);
        if (deployment == null) return this;    // No longer deployed in this zone.
        return with(deployment.withClusterInfo(clusterInfo));

    }

    public LockedApplication with(Zone zone, DeploymentMetrics deploymentMetrics) {
        Deployment deployment = deployments().get(zone);
        if (deployment == null) return this;    // No longer deployed in this zone.
        return with(deployment.withMetrics(deploymentMetrics));
    }

    public LockedApplication withoutDeploymentIn(Zone zone) {
        Map<Zone, Deployment> deployments = new LinkedHashMap<>(deployments());
        deployments.remove(zone);
        return new LockedApplication(new Application(id(), deploymentSpec(), validationOverrides(),
                                                     deployments, deploymentJobs(), deploying(),
                                                     hasOutstandingChange(), ownershipIssueId(), metrics()), lock);
    }

    public LockedApplication withoutDeploymentJob(DeploymentJobs.JobType jobType) {
        DeploymentJobs deploymentJobs = deploymentJobs().without(jobType);
        return new LockedApplication(new Application(id(), deploymentSpec(), validationOverrides(),
                                                     deployments(), deploymentJobs, deploying(),
                                                     hasOutstandingChange(), ownershipIssueId(), metrics()), lock);
    }

    public LockedApplication with(DeploymentSpec deploymentSpec) {
        return new LockedApplication(new Application(id(), deploymentSpec, validationOverrides(),
                                                     deployments(), deploymentJobs(), deploying(),
                                                     hasOutstandingChange(), ownershipIssueId(), metrics()), lock);
    }

    public LockedApplication with(ValidationOverrides validationOverrides) {
        return new LockedApplication(new Application(id(), deploymentSpec(), validationOverrides,
                                                     deployments(), deploymentJobs(), deploying(),
                                                     hasOutstandingChange(), ownershipIssueId(), metrics()), lock);
    }

    public LockedApplication withDeploying(Optional<Change> deploying) {
        return new LockedApplication(new Application(id(), deploymentSpec(), validationOverrides(),
                                                     deployments(), deploymentJobs(), deploying,
                                                     hasOutstandingChange(), ownershipIssueId(), metrics()), lock);
    }

    public LockedApplication withOutstandingChange(boolean outstandingChange) {
        return new LockedApplication(new Application(id(), deploymentSpec(), validationOverrides(),
                                                     deployments(), deploymentJobs(), deploying(),
                                                     outstandingChange, ownershipIssueId(), metrics()), lock);
    }

    public LockedApplication withOwnershipIssueId(IssueId issueId) {
        return new LockedApplication(new Application(id(), deploymentSpec(), validationOverrides(),
                                                     deployments(), deploymentJobs(), deploying(),
                                                     hasOutstandingChange(), Optional.of(issueId), metrics()), lock);
    }

    public LockedApplication with(MetricsService.ApplicationMetrics metrics) {
        return new LockedApplication(new Application(id(), deploymentSpec(), validationOverrides(),
                                                     deployments(), deploymentJobs(), deploying(),
                                                     hasOutstandingChange(), ownershipIssueId(), metrics), lock);
    }

    public Version deployVersionFor(DeploymentJobs.JobType jobType, Controller controller) {
        return jobType == JobType.component
               ? controller.systemVersion()
               : deployVersionIn(jobType.zone(controller.system()).get(), controller);
    }

    public Optional<ApplicationRevision> deployRevisionFor(DeploymentJobs.JobType jobType, Controller controller) {
        return jobType == JobType.component
               ? Optional.empty()
               : deployRevisionIn(jobType.zone(controller.system()).get());
    }

}
