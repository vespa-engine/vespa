package com.yahoo.vespa.hosted.controller;

import com.yahoo.component.Version;
import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.application.api.ValidationOverrides;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.curator.Lock;
import com.yahoo.vespa.hosted.controller.api.integration.organization.IssueId;
import com.yahoo.vespa.hosted.controller.application.ApplicationRevision;
import com.yahoo.vespa.hosted.controller.application.Change;
import com.yahoo.vespa.hosted.controller.application.Change.ApplicationChange;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType;

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
              application.hasOutstandingChange(), application.ownershipIssueId());
        this.lock = Objects.requireNonNull(lock, "lock cannot be null");
    }

    public LockedApplication withProjectId(long projectId) {
        return new LockedApplication(new Application(id(), deploymentSpec(), validationOverrides(), deployments(),
                                                     deploymentJobs().withProjectId(projectId), deploying(),
                                                     hasOutstandingChange(), ownershipIssueId()), lock);
    }

    public LockedApplication with(IssueId issueId) {
        return new LockedApplication(new Application(id(), deploymentSpec(), validationOverrides(), deployments(),
                                                     deploymentJobs().with(issueId), deploying(),
                                                     hasOutstandingChange(), ownershipIssueId()), lock);
    }

    public LockedApplication withJobCompletion(DeploymentJobs.JobReport report, Instant notificationTime,
                                               Controller controller) {
        return new LockedApplication(new Application(id(), deploymentSpec(), validationOverrides(),
                                                     deployments(),
                                                     deploymentJobs().withCompletion(report, notificationTime,
                                                                                     controller),
                                                     deploying(), hasOutstandingChange(), ownershipIssueId()), lock);
    }

    public LockedApplication withJobTriggering(DeploymentJobs.JobType type, Optional<Change> change,
                                               String reason, Instant triggerTime, Controller controller) {
        return new LockedApplication(new Application(id(), deploymentSpec(), validationOverrides(), deployments(),
                                                     deploymentJobs().withTriggering(type,
                                                                                     change,
                                                                                     deployVersionFor(type, controller),
                                                                                     deployRevisionFor(type, controller),
                                                                                     reason,
                                                                                     triggerTime),
                                                     deploying(), hasOutstandingChange(), ownershipIssueId()), lock);
    }

    public LockedApplication with(Deployment deployment) {
        Map<Zone, Deployment> deployments = new LinkedHashMap<>(deployments());
        deployments.put(deployment.zone(), deployment);
        return new LockedApplication(new Application(id(), deploymentSpec(), validationOverrides(),
                                                     deployments, deploymentJobs(), deploying(),
                                                     hasOutstandingChange(), ownershipIssueId()), lock);
    }

    public LockedApplication with(DeploymentJobs deploymentJobs) {
        return new LockedApplication(new Application(id(), deploymentSpec(), validationOverrides(),
                                                     deployments(), deploymentJobs, deploying(),
                                                     hasOutstandingChange(), ownershipIssueId()), lock);
    }

    public LockedApplication withoutDeploymentIn(Zone zone) {
        Map<Zone, Deployment> deployments = new LinkedHashMap<>(deployments());
        deployments.remove(zone);
        return new LockedApplication(new Application(id(), deploymentSpec(), validationOverrides(),
                                                     deployments, deploymentJobs(), deploying(),
                                                     hasOutstandingChange(), ownershipIssueId()), lock);
    }

    public LockedApplication withoutDeploymentJob(DeploymentJobs.JobType jobType) {
        DeploymentJobs deploymentJobs = deploymentJobs().without(jobType);
        return new LockedApplication(new Application(id(), deploymentSpec(), validationOverrides(),
                                                     deployments(), deploymentJobs, deploying(),
                                                     hasOutstandingChange(), ownershipIssueId()), lock);
    }

    public LockedApplication with(DeploymentSpec deploymentSpec) {
        return new LockedApplication(new Application(id(), deploymentSpec, validationOverrides(),
                                                     deployments(), deploymentJobs(), deploying(),
                                                     hasOutstandingChange(), ownershipIssueId()), lock);
    }

    public LockedApplication with(ValidationOverrides validationOverrides) {
        return new LockedApplication(new Application(id(), deploymentSpec(), validationOverrides,
                                                     deployments(), deploymentJobs(), deploying(),
                                                     hasOutstandingChange(), ownershipIssueId()), lock);
    }

    public LockedApplication withDeploying(Optional<Change> deploying) {
        return new LockedApplication(new Application(id(), deploymentSpec(), validationOverrides(),
                                                     deployments(), deploymentJobs(), deploying,
                                                     hasOutstandingChange(), ownershipIssueId()), lock);
    }

    public LockedApplication withOutstandingChange(boolean outstandingChange) {
        return new LockedApplication(new Application(id(), deploymentSpec(), validationOverrides(),
                                                     deployments(), deploymentJobs(), deploying(),
                                                     outstandingChange, ownershipIssueId()), lock);
    }

    public LockedApplication withOwnershipIssueId(IssueId issueId) {
        return new LockedApplication(new Application(id(), deploymentSpec(), validationOverrides(),
                                                     deployments(), deploymentJobs(), deploying(),
                                                     hasOutstandingChange(), Optional.of(issueId)), lock);
    }

    private Version deployVersionFor(DeploymentJobs.JobType jobType, Controller controller) {
        return jobType == JobType.component
               ? controller.systemVersion()
               : deployVersionIn(jobType.zone(controller.system()).get(), controller);
    }

    private Optional<ApplicationRevision> deployRevisionFor(DeploymentJobs.JobType jobType, Controller controller) {
        return jobType == JobType.component
               ? Optional.empty()
               : deployRevisionIn(jobType.zone(controller.system()).get());
    }

    /** Returns the revision a new deployment to this zone should use for this application, or empty if we don't know */
    private Optional<ApplicationRevision> deployRevisionIn(Zone zone) {
        if (deploying().isPresent() && deploying().get() instanceof ApplicationChange)
            return ((Change.ApplicationChange) deploying().get()).revision();

        return revisionIn(zone);
    }

    /** Returns the revision this application is or should be deployed with in the given zone, or empty if unknown. */
    private Optional<ApplicationRevision> revisionIn(Zone zone) {
        return Optional.ofNullable(deployments().get(zone)).map(Deployment::revision);
    }

}
