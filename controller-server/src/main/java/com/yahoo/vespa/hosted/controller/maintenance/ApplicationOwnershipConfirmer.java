package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.Tenant;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.TenantType;
import com.yahoo.vespa.hosted.controller.api.identifiers.PropertyId;
import com.yahoo.vespa.hosted.controller.api.identifiers.TenantId;
import com.yahoo.vespa.hosted.controller.api.integration.organization.OwnershipIssues;
import com.yahoo.vespa.hosted.controller.api.integration.organization.IssueId;
import com.yahoo.vespa.hosted.controller.api.integration.organization.User;
import com.yahoo.vespa.hosted.controller.application.ApplicationList;

import java.time.Duration;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.logging.Level;

/**
 * Periodically request application ownership confirmation through filing issues.
 *
 * When to file new issues, escalate inactive ones, etc., is handled by the enclosed OwnershipIssues.
 *
 * @author jvenstad
 */
public class ApplicationOwnershipConfirmer extends Maintainer {

    private final OwnershipIssues ownershipIssues;

    public ApplicationOwnershipConfirmer(Controller controller, Duration interval, JobControl jobControl, OwnershipIssues ownershipIssues) {
        super(controller, interval, jobControl);
        this.ownershipIssues = ownershipIssues;
    }

    @Override
    protected void maintain() {
        confirmApplicationOwnerships();
        ensureConfirmationResponses();
    }

    /** File an ownership issue with the owners of all applications we know about. */
    private void confirmApplicationOwnerships() {
        ApplicationList.from(controller().applications().asList())
                .notPullRequest()
                .hasProductionDeployment()
                .asList()
                .forEach(application -> {
                    try {
                        Tenant tenant = ownerOf(application.id());
                        Optional<IssueId> ourIssueId = application.ownershipIssueId();
                        ourIssueId = tenant.tenantType() == TenantType.USER
                                ? ownershipIssues.confirmOwnership(ourIssueId, application.id(), userFor(tenant))
                                : ownershipIssues.confirmOwnership(ourIssueId, application.id(), propertyIdFor(tenant));
                        ourIssueId.ifPresent(issueId -> store(issueId, application.id()));
                    }
                    catch (RuntimeException e) { // Catch errors due to wrong data in the controller, or issues client timeout.
                        log.log(Level.WARNING, "Exception caught when attempting to file an issue for " + application.id(), e);
                    }
                });

    }

    /** Escalate ownership issues which have not been closed before a defined amount of time has passed. */
    private void ensureConfirmationResponses() {
        for (Application application : controller().applications().asList())
            application.ownershipIssueId().ifPresent(issueId -> {
                try {
                    ownershipIssues.ensureResponse(issueId, ownerOf(application.id()).getPropertyId());
                }
                catch (RuntimeException e) {
                    log.log(Level.WARNING, "Exception caught when attempting to escalate issue with id " + issueId, e);
                }
            });
    }

    private Tenant ownerOf(ApplicationId applicationId) {
        return controller().tenants().tenant(new TenantId(applicationId.tenant().value()))
                .orElseThrow(() -> new IllegalStateException("No tenant found for application " + applicationId));
    }

    protected User userFor(Tenant tenant) {
        return User.from(tenant.getId().id().replaceFirst("by-", ""));
    }

    protected PropertyId propertyIdFor(Tenant tenant) {
        return tenant.getPropertyId()
                .orElseThrow(() -> new NoSuchElementException("No PropertyId is listed for non-user tenant " + tenant));
    }

    protected void store(IssueId issueId, ApplicationId applicationId) {
        controller().applications().lockIfPresent(applicationId, application ->
                controller().applications().store(application.withOwnershipIssueId(issueId)));
    }
}
