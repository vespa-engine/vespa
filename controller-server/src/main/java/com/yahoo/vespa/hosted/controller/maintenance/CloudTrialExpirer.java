// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.flags.ListFlag;
import com.yahoo.vespa.flags.PermanentFlags;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.billing.PlanId;
import com.yahoo.vespa.hosted.controller.tenant.LastLoginInfo;
import com.yahoo.vespa.hosted.controller.tenant.Tenant;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Expires unused tenants from Vespa Cloud.
 *
 * TODO: Should support sending notifications some time before the various expiry events happen.
 *
 * @author ogronnesby
 */
public class CloudTrialExpirer extends ControllerMaintainer {
    private static final Logger log = Logger.getLogger(CloudTrialExpirer.class.getName());

    private static final Duration loginExpiry = Duration.ofDays(14);
    private final ListFlag<String> extendedTrialTenants;

    public CloudTrialExpirer(Controller controller, Duration interval) {
        super(controller, interval, null, SystemName.allOf(SystemName::isPublic));
        this.extendedTrialTenants = PermanentFlags.EXTENDED_TRIAL_TENANTS.bindTo(controller().flagSource());
    }

    @Override
    protected double maintain() {
        if (controller().system().equals(SystemName.PublicCd)) {
            tombstoneNonePlanTenants();
        }
        moveInactiveTenantsToNonePlan();
        return 1.0;
    }

    private void moveInactiveTenantsToNonePlan() {
        var predicate = tenantReadersNotLoggedIn(loginExpiry)
                .and(this::tenantHasTrialPlan);

        forTenant("'none' plan", predicate, this::setPlanNone);
    }

    private void tombstoneNonePlanTenants() {
        // tombstone tenants that are inactive 14 days after being set as 'none'
        var expiry = loginExpiry.plus(loginExpiry);
        var predicate = tenantReadersNotLoggedIn(expiry).and(this::tenantHasNonePlan);
        forTenant("tombstoned", predicate, this::tombstoneTenants);
    }

    private void forTenant(String name, Predicate<Tenant> p, Consumer<List<Tenant>> c) {
        var predicate = p.and(this::tenantIsCloudTenant)
                .and(this::tenantIsNotExemptFromExpiry)
                .and(this::tenantHasNoDeployments);

        var tenants = controller().tenants().asList().stream()
                .filter(predicate)
                .collect(Collectors.toList());

        if (! tenants.isEmpty()) {
            var tenantNames = tenants.stream().map(Tenant::name).map(TenantName::value).collect(Collectors.joining(", "));
            log.info("Setting tenants as " + name + ": " + tenantNames);
        }

        c.accept(tenants);
    }

    private boolean tenantIsCloudTenant(Tenant tenant) {
        return tenant.type() == Tenant.Type.cloud;
    }

    private Predicate<Tenant> tenantReadersNotLoggedIn(Duration duration) {
        // returns true if no user has logged in to the tenant after (now - duration)
        return (Tenant tenant) -> {
            var timeLimit = controller().clock().instant().minus(duration);
            return tenant.lastLoginInfo().get(LastLoginInfo.UserLevel.user)
                    .map(instant -> instant.isBefore(timeLimit))
                    .orElse(false);
        };
    }

    private boolean tenantHasTrialPlan(Tenant tenant) {
        var planId = controller().serviceRegistry().billingController().getPlan(tenant.name());
        return "trial".equals(planId.value());
    }

    private boolean tenantHasNonePlan(Tenant tenant) {
        var planId = controller().serviceRegistry().billingController().getPlan(tenant.name());
        return "none".equals(planId.value());
    }

    private boolean tenantIsNotExemptFromExpiry(Tenant tenant) {
        return ! extendedTrialTenants.value().contains(tenant.name().value());
    }

    private boolean tenantHasNoDeployments(Tenant tenant) {
        return controller().applications().asList(tenant.name()).stream()
                .flatMap(app -> app.instances().values().stream())
                .mapToLong(instance -> instance.deployments().values().size())
                .sum() == 0;
    }

    private void setPlanNone(List<Tenant> tenants) {
        tenants.forEach(tenant -> {
            controller().serviceRegistry().billingController().setPlan(tenant.name(), PlanId.from("none"), false);
        });
    }

    private void tombstoneTenants(List<Tenant> tenants) {
        tenants.forEach(tenant -> {
            deleteApplicationsWithNoDeployments(tenant);
            controller().tenants().delete(tenant.name(), Optional.empty(), false);
        });
    }

    private void deleteApplicationsWithNoDeployments(Tenant tenant) {
        controller().applications().asList(tenant.name()).forEach(application -> {
            // this only removes applications with no active deployments
            controller().applications().deleteApplication(application.id(), Optional.empty());
        });
    }
}
