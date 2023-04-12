// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.ApplicationController;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.Instance;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.integration.organization.ApplicationSummary;
import com.yahoo.vespa.hosted.controller.api.integration.organization.IssueId;
import com.yahoo.vespa.hosted.controller.api.integration.organization.OwnershipIssues;
import com.yahoo.vespa.hosted.controller.api.integration.organization.User;
import com.yahoo.vespa.hosted.controller.application.ApplicationList;
import com.yahoo.vespa.hosted.controller.application.TenantAndApplicationId;
import com.yahoo.vespa.hosted.controller.tenant.Tenant;
import com.yahoo.yolean.Exceptions;

import java.time.Duration;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

/**
 * Periodically request application ownership confirmation through filing issues.
 *
 * When to file new issues, escalate inactive ones, etc., is handled by the enclosed OwnershipIssues.
 *
 * @author jonmv
 */
public class ApplicationOwnershipConfirmer extends ControllerMaintainer {

    private final OwnershipIssues ownershipIssues;
    private final ApplicationController applications;
    private final int shards;

    public ApplicationOwnershipConfirmer(Controller controller, Duration interval, OwnershipIssues ownershipIssues) {
        this(controller, interval, ownershipIssues, 24);
    }

    public ApplicationOwnershipConfirmer(Controller controller, Duration interval, OwnershipIssues ownershipIssues, int shards) {
        super(controller, interval);
        this.ownershipIssues = ownershipIssues;
        this.applications = controller.applications();
        if (shards <= 0) throw new IllegalArgumentException("shards must be a positive number, but got " + shards);
        this.shards = shards;
    }

    @Override
    protected double maintain() {
        return ( confirmApplicationOwnerships() +
                 ensureConfirmationResponses() +
                 updateConfirmedApplicationOwners() )
                / 3;
    }

    /** File an ownership issue with the owners of all applications we know about. */
    private double confirmApplicationOwnerships() {
        AtomicInteger attempts = new AtomicInteger(0);
        AtomicInteger failures = new AtomicInteger(0);
        applications()
                       .withProjectId()
                       .withProductionDeployment()
                       .asList()
                       .stream()
                       .filter(application -> application.createdAt().isBefore(controller().clock().instant().minus(Duration.ofDays(90))))
                       .filter(application -> isInCurrentShard(application.id()))
                       .forEach(application -> {
                           try {
                               attempts.incrementAndGet();
                               tenantOf(application.id()).contact().flatMap(contact -> {
                                   return ownershipIssues.confirmOwnership(application.ownershipIssueId(),
                                                                           summaryOf(application.id()),
                                                                           determineAssignee(application),
                                                                           contact);
                               }).ifPresent(newIssueId -> store(newIssueId, application.id()));
                           }
                           catch (RuntimeException e) { // Catch errors due to wrong data in the controller, or issues client timeout.
                               failures.incrementAndGet();
                               log.log(Level.INFO, "Exception caught when attempting to file an issue for '" + application.id() + "': " + Exceptions.toMessageString(e));
                           }
                       });
        return asSuccessFactorDeviation(attempts.get(), failures.get());
    }

    private boolean isInCurrentShard(TenantAndApplicationId id) {
        double participants = Math.max(1, controller().curator().cluster().size());
        long ticksSinceEpoch = Math.round((controller().clock().millis() * participants / interval().toMillis()));
        return (ticksSinceEpoch + id.hashCode()) % shards == 0;
    }

    private ApplicationSummary summaryOf(TenantAndApplicationId application) {
        var app = applications.requireApplication(application);
        var metrics = new HashMap<DeploymentId, ApplicationSummary.Metric>();
        for (Instance instance : app.instances().values()) {
            for (var kv : instance.deployments().entrySet()) {
                var zone = kv.getKey();
                var deploymentMetrics = kv.getValue().metrics();
                metrics.put(new DeploymentId(instance.id(), zone),
                            new ApplicationSummary.Metric(deploymentMetrics.documentCount(),
                                                          deploymentMetrics.queriesPerSecond(),
                                                          deploymentMetrics.writesPerSecond()));
            }
        }
        return new ApplicationSummary(app.id().defaultInstance(), app.activity().lastQueried(), app.activity().lastWritten(),
                                      app.revisions().last().flatMap(version -> version.buildTime()), metrics);
    }

    /** Escalate ownership issues which have not been closed before a defined amount of time has passed. */
    private double ensureConfirmationResponses() {
        AtomicInteger attempts = new AtomicInteger(0);
        AtomicInteger failures = new AtomicInteger(0);
        for (Application application : applications())
            if (isInCurrentShard(application.id()))
                application.ownershipIssueId().ifPresent(issueId -> {
                    try {
                        attempts.incrementAndGet();
                        Tenant tenant = tenantOf(application.id());
                        ownershipIssues.ensureResponse(issueId, tenant.contact());
                    }
                    catch (RuntimeException e) {
                        failures.incrementAndGet();
                        log.log(Level.INFO, "Exception caught when attempting to escalate issue with id '" + issueId + "': " + Exceptions.toMessageString(e));
                    }
                });
        return asSuccessFactorDeviation(attempts.get(), failures.get());
    }

    private double updateConfirmedApplicationOwners() {
        AtomicInteger attempts = new AtomicInteger(0);
        AtomicInteger failures = new AtomicInteger(0);
        applications()
                .withProjectId()
                .withProductionDeployment()
                .asList()
                .stream()
                .filter(application -> isInCurrentShard(application.id()))
                .filter(application -> application.ownershipIssueId().isPresent())
                .forEach(application -> {
                    attempts.incrementAndGet();
                    IssueId issueId = application.ownershipIssueId().get();
                    try {
                        ownershipIssues.getConfirmedOwner(issueId).ifPresent(owner -> {
                            controller().applications().lockApplicationIfPresent(application.id(), lockedApplication ->
                                    controller().applications().store(lockedApplication.withOwner(owner)));
                        });
                    }
                    catch (RuntimeException e) {
                        failures.incrementAndGet();
                        log.log(Level.INFO, "Exception caught when attempting to find confirmed owner of issue with id '" + issueId + "': " + Exceptions.toMessageString(e));
                    }
                });
        return asSuccessFactorDeviation(attempts.get(), failures.get());
    }

    private ApplicationList applications() {
        return ApplicationList.from(controller().applications().readable());
    }

    private User determineAssignee(Application application) {
        return application.owner().orElse(null);
    }

    private Tenant tenantOf(TenantAndApplicationId applicationId) {
        return controller().tenants().get(applicationId.tenant())
                .orElseThrow(() -> new IllegalStateException("No tenant found for application " + applicationId));
    }

    protected void store(IssueId issueId, TenantAndApplicationId applicationId) {
        controller().applications().lockApplicationIfPresent(applicationId, application ->
                controller().applications().store(application.withOwnershipIssueId(issueId)));
    }
}
