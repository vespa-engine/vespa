// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.curator.Lock;
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

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;

import static com.yahoo.vespa.hosted.controller.versions.VespaVersion.Confidence.broken;

/**
 * Maintenance job which files issues for tenants when they have jobs which fails continuously
 * and escalates issues which are not handled in a timely manner.
 *
 * @author jvenstad
 */
public class DeploymentIssueReporter extends Maintainer {

    static final Duration maxFailureAge = Duration.ofDays(2);
    static final Duration maxInactivity = Duration.ofDays(4);
    static final Duration upgradeGracePeriod = Duration.ofHours(4);

    private final DeploymentIssues deploymentIssues;

    DeploymentIssueReporter(Controller controller, DeploymentIssues deploymentIssues, Duration maintenanceInterval, JobControl jobControl) {
        super(controller, maintenanceInterval, jobControl);
        this.deploymentIssues = deploymentIssues;
    }

    @Override
    protected void maintain() {
        maintainDeploymentIssues(controller().applications().asList());
        maintainPlatformIssue(controller().applications().asList());
        escalateInactiveDeploymentIssues(controller().applications().asList());
    }

    /**
     * File issues for applications which have failed deployment for longer than maxFailureAge
     * and store the issue id for the filed issues. Also, clear the issueIds of applications
     * where deployment has not failed for this amount of time.
     */
    private void maintainDeploymentIssues(List<Application> applications) {
        Set<ApplicationId> failingApplications = ApplicationList.from(applications)
                .failingApplicationChangeSince(controller().clock().instant().minus(maxFailureAge))
                .asList().stream()
                .map(Application::id)
                .collect(Collectors.toSet());

        for (Application application : applications)
            if (failingApplications.contains(application.id()))
                fileDeploymentIssueFor(application.id());
            else
                storeIssueId(application.id(), null);
    }

    /**
     * When the confidence for the system version is BROKEN, file an issue listing the
     * applications that have been failing the upgrade to the system version for
     * longer than the set grace period, or update this list if the issue already exists.
     */
    private void maintainPlatformIssue(List<Application> applications) {
        if ( ! (controller().versionStatus().version(controller().systemVersion()).confidence() == broken))
            return;

        List<ApplicationId> failingApplications = ApplicationList.from(applications)
                .failingUpgradeToVersionSince(controller().systemVersion(), controller().clock().instant().minus(upgradeGracePeriod))
                .asList().stream()
                .map(Application::id)
                .collect(Collectors.toList());

        if ( ! failingApplications.isEmpty())
            deploymentIssues.fileUnlessOpen(failingApplications, controller().systemVersion());
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
            storeIssueId(applicationId, issueId);
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

    private void storeIssueId(ApplicationId id, IssueId issueId) {
        try (Lock lock = controller().applications().lock(id)) {
            controller().applications().get(id, lock).ifPresent(
                    application -> controller().applications().store(application.with(issueId))
            );
        }
    }

}
