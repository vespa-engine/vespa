// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.Instance;
import com.yahoo.vespa.hosted.controller.ApplicationController;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.organization.ApplicationSummary;
import com.yahoo.vespa.hosted.controller.api.integration.organization.IssueId;
import com.yahoo.vespa.hosted.controller.api.integration.organization.OwnershipIssues;
import com.yahoo.vespa.hosted.controller.api.integration.organization.User;
import com.yahoo.vespa.hosted.controller.application.ApplicationList;
import com.yahoo.vespa.hosted.controller.application.TenantAndApplicationId;
import com.yahoo.vespa.hosted.controller.tenant.Tenant;
import com.yahoo.vespa.hosted.controller.tenant.UserTenant;
import com.yahoo.yolean.Exceptions;

import java.time.Duration;
import java.util.HashMap;
import java.util.Optional;
import java.util.logging.Level;

/**
 * Periodically request application ownership confirmation through filing issues.
 *
 * When to file new issues, escalate inactive ones, etc., is handled by the enclosed OwnershipIssues.
 *
 * @author jonmv
 */
public class ApplicationOwnershipConfirmer extends Maintainer {

    private final OwnershipIssues ownershipIssues;
    private final ApplicationController applications;

    public ApplicationOwnershipConfirmer(Controller controller, Duration interval, JobControl jobControl, OwnershipIssues ownershipIssues) {
        super(controller, interval, jobControl);
        this.ownershipIssues = ownershipIssues;
        this.applications = controller.applications();
    }

    @Override
    protected void maintain() {
        confirmApplicationOwnerships();
        ensureConfirmationResponses();
        updateConfirmedApplicationOwners();
    }

    /** File an ownership issue with the owners of all applications we know about. */
    private void confirmApplicationOwnerships() {
        ApplicationList.from(controller().applications().asList())
                       .withProjectId()
                       .withProductionDeployment()
                       .asList()
                       .stream()
                       .filter(application -> application.createdAt().isBefore(controller().clock().instant().minus(Duration.ofDays(90))))
                       .forEach(application -> {
                           try {
                               Tenant tenant = tenantOf(application.id());
                               tenant.contact().ifPresent(contact -> { // TODO jvenstad: Makes sense to require, and run this only in main?
                                   ownershipIssues.confirmOwnership(application.ownershipIssueId(),
                                                                    summaryOf(application.id()),
                                                                    determineAssignee(tenant, application),
                                                                    contact)
                                                  .ifPresent(newIssueId -> store(newIssueId, application.id()));
                               });
                           }
                           catch (RuntimeException e) { // Catch errors due to wrong data in the controller, or issues client timeout.
                               log.log(Level.INFO, "Exception caught when attempting to file an issue for '" + application.id() + "': " + Exceptions.toMessageString(e));
                           }
                       });

    }

    private ApplicationSummary summaryOf(TenantAndApplicationId application) {
        var app = applications.requireApplication(application);
        var metrics = new HashMap<ZoneId, ApplicationSummary.Metric>();
        for (Instance instance : app.instances().values())
            for (var kv : instance.deployments().entrySet()) {
                var zone = kv.getKey();
                var deploymentMetrics = kv.getValue().metrics();
                metrics.put(zone, new ApplicationSummary.Metric(deploymentMetrics.documentCount(),
                                                                deploymentMetrics.queriesPerSecond(),
                                                                deploymentMetrics.writesPerSecond()));
            }
        return new ApplicationSummary(app.id().defaultInstance(), app.activity().lastQueried(), app.activity().lastWritten(),
                                      app.latestVersion().flatMap(version -> version.buildTime()), metrics);
    }

    /** Escalate ownership issues which have not been closed before a defined amount of time has passed. */
    private void ensureConfirmationResponses() {
        for (Application application : controller().applications().asList())
            application.ownershipIssueId().ifPresent(issueId -> {
                try {
                    Tenant tenant = tenantOf(application.id());
                    ownershipIssues.ensureResponse(issueId, tenant.type() == Tenant.Type.athenz ? tenant.contact() : Optional.empty());
                }
                catch (RuntimeException e) {
                    log.log(Level.INFO, "Exception caught when attempting to escalate issue with id '" + issueId + "': " + Exceptions.toMessageString(e));
                }
            });
    }

    private void updateConfirmedApplicationOwners() {
        ApplicationList.from(controller().applications().asList())
                       .withProjectId()
                       .withProductionDeployment()
                       .asList()
                       .stream()
                       .filter(application -> application.ownershipIssueId().isPresent())
                       .forEach(application -> {
                           IssueId ownershipIssueId = application.ownershipIssueId().get();
                           ownershipIssues.getConfirmedOwner(ownershipIssueId).ifPresent(owner -> {
                               controller().applications().lockApplicationIfPresent(application.id(), lockedApplication ->
                                       controller().applications().store(lockedApplication.withOwner(owner)));
                           });
                       });
    }

    private User determineAssignee(Tenant tenant, Application application) {
        return application.owner().orElse(tenant instanceof UserTenant ? userFor(tenant) : null);
    }

    private Tenant tenantOf(TenantAndApplicationId applicationId) {
        return controller().tenants().get(applicationId.tenant())
                .orElseThrow(() -> new IllegalStateException("No tenant found for application " + applicationId));
    }

    protected User userFor(Tenant tenant) {
        return User.from(tenant.name().value().replaceFirst(Tenant.userPrefix, ""));
    }

    protected void store(IssueId issueId, TenantAndApplicationId applicationId) {
        controller().applications().lockApplicationIfPresent(applicationId, application ->
                controller().applications().store(application.withOwnershipIssueId(issueId)));
    }
}
