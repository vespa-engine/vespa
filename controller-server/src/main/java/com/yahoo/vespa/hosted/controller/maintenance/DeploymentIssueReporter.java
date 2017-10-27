// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.ApplicationId;
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

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.logging.Level;

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
        maintainDeploymentIssues(controller().applications().list().asList());
        escalateInactiveDeploymentIssues(controller().applications().list().asList());
    }

    /**
     * File issues for applications which have failed deployment for longer than maxFailureAge
     * and store the issue id for the filed issues. Also, clear the issueIds of applications
     * where deployment has not failed for this amount of time.
     */
    private void maintainDeploymentIssues(List<ApplicationList.Entry> applications) {
        List<ApplicationId> failingApplications = new ArrayList<>();
        for (ApplicationList.Entry entry : applications)
            if (hasFailuresOlderThanThreshold(entry.deploymentJobs()))
                failingApplications.add(entry.id());
            else
                controller().applications().setIssueId(entry.id(), null);

        // TODO: Change this logic, depending on the controller's definition of BROKEN, whether it updates applications
        // TODO: to an older version when the system version is BROKEN, etc..
        if (failingApplications.size() > 0.2 * applications.size())
            deploymentIssues.fileUnlessOpen(failingApplications);
        else
            failingApplications.forEach(this::fileDeploymentIssueFor);
    }

    /** Returns whether deploymentJobs has a job which has been failing since before failureThreshold. */
    private boolean hasFailuresOlderThanThreshold(DeploymentJobs deploymentJobs) {
        return deploymentJobs.hasFailures()
               && deploymentJobs.failingSince().isBefore(controller().clock().instant().minus(maxFailureAge));
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
    private void escalateInactiveDeploymentIssues(Collection<ApplicationList.Entry> applications) {
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
