// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.Tenant;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.TenantType;
import com.yahoo.vespa.hosted.controller.api.identifiers.PropertyId;
import com.yahoo.vespa.hosted.controller.api.identifiers.TenantId;
import com.yahoo.vespa.hosted.controller.api.integration.organization.DeploymentIssues;
import com.yahoo.vespa.hosted.controller.api.integration.organization.IssueId;
import com.yahoo.vespa.hosted.controller.api.integration.organization.User;
import com.yahoo.vespa.hosted.controller.application.ApplicationList;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs;
import com.yahoo.vespa.hosted.controller.application.JobStatus;
import com.yahoo.vespa.hosted.controller.versions.VespaVersion;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Maintenance job which files issues for tenants when they have jobs which fails continuously
 * and escalates issues which are not handled in a timely manner.
 *
 * @author jvenstad
 */
public class DeploymentIssueReporter extends Maintainer {

    static final Duration maxFailureAge = Duration.ofDays(2);
    static final Duration maxInactivity = Duration.ofDays(4);

    private final DeploymentIssues deploymentIssues;

    DeploymentIssueReporter(Controller controller, DeploymentIssues deploymentIssues, Duration maintenanceInterval, JobControl jobControl) {
        super(controller, maintenanceInterval, jobControl);
        this.deploymentIssues = deploymentIssues;
    }

    @Override
    protected void maintain() {
        maintainDeploymentIssues(controller().applications().asList());
        escalateInactiveDeploymentIssues(controller().applications().asList());
    }

    /**
     * File issues for applications which have failed deployment for longer than maxFailureAge
     * and store the issue id for the filed issues. Also, clear the issueIds of applications
     * where deployment has not failed for this amount of time.
     */
    private void maintainDeploymentIssues(List<Application> applications) {
        List<ApplicationId> failingApplications = new ArrayList<>();
        for (Application application : applications)
            if (oldApplicationChangeFailuresIn(application.deploymentJobs()))
                failingApplications.add(application.id());
            else
                controller().applications().setIssueId(application.id(), null);

        failingApplications.forEach(this::fileDeploymentIssueFor);

        if (controller().versionStatus().version(controller().systemVersion()).confidence() == VespaVersion.Confidence.broken)
            deploymentIssues.fileUnlessOpen(ApplicationList.from(applications)
                                                    .upgradingTo(controller().systemVersion())
                                                    .asList().stream()
                                                    .map(Application::id)
                                                    .collect(Collectors.toList()),
                                            controller().systemVersion());
    }

    private boolean oldApplicationChangeFailuresIn(DeploymentJobs jobs) {
        if (!jobs.hasFailures()) return false;

        Optional<Instant> oldestApplicationChangeFailure = jobs.jobStatus().values().stream()
                .filter(job -> ! job.isSuccess() && failureCausedByApplicationChange(job))
                .map(job -> job.firstFailing().get().at())
                .min(Comparator.naturalOrder());

        return oldestApplicationChangeFailure.isPresent()
               && oldestApplicationChangeFailure.get().isBefore(controller().clock().instant().minus(maxFailureAge));
    }

    private boolean failureCausedByApplicationChange(JobStatus job) {
        if ( ! job.lastSuccess().isPresent()) return true; // An application which never succeeded is surely bad.
        if ( ! job.firstFailing().get().version().equals(job.lastSuccess().get().version())) return false; // Version change may be to blame.
        if ( ! job.lastSuccess().get().revision().isPresent()) return true; // Indicates the component job, which is always an application change.
        return ! job.firstFailing().get().revision().equals(job.lastSuccess().get().revision()); // Return whether there is an application change.
    }

    private Tenant ownerOf(ApplicationId applicationId) {
        return controller().tenants().tenant(new TenantId(applicationId.tenant().value()))
                .orElseThrow(() -> new IllegalStateException("No tenant found for application " + applicationId));
    }

    private User userFor(Tenant tenant) {
        return User.from(tenant.getId().id().replaceFirst("by-", ""));
    }

    private PropertyId propertyIdFor(Tenant tenant) {
        return tenant.getPropertyId()
                .orElseThrow(() -> new NoSuchElementException("No PropertyId is listed for non-user tenant " + tenant));
    }

    /** File an issue for applicationId, if it doesn't already have an open issue associated with it. */
    private void fileDeploymentIssueFor(ApplicationId applicationId) {
        try {
            Tenant tenant = ownerOf(applicationId);
            Optional<IssueId> ourIssueId = controller().applications().require(applicationId).deploymentJobs().issueId();
            IssueId issueId = tenant.tenantType() == TenantType.USER
                              ? deploymentIssues.fileUnlessOpen(ourIssueId, applicationId, userFor(tenant))
                              : deploymentIssues.fileUnlessOpen(ourIssueId, applicationId, propertyIdFor(tenant));
            controller().applications().setIssueId(applicationId, issueId);
        }
        catch (RuntimeException e) { // Catch errors due to wrong data in the controller, or issues client timeout.
            log.log(Level.WARNING, "Exception caught when attempting to file an issue for " + applicationId, e);
        }
    }

    /** Escalate issues for which there has been no activity for a certain amount of time. */
    private void escalateInactiveDeploymentIssues(Collection<Application> applications) {
        applications.forEach(application -> application.deploymentJobs().issueId().ifPresent(issueId -> {
            try {
                deploymentIssues.escalateIfInactive(issueId, ownerOf(application.id()).getPropertyId(), maxInactivity);
            }
            catch (RuntimeException e) {
                log.log(Level.WARNING, "Exception caught when attempting to escalate issue with id " + issueId, e);
            }
        }));
    }

}
