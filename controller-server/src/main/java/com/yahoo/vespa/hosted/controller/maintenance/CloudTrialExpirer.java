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
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Expires unused tenants from Vespa Cloud.
 * <p>
 * TODO: Should support sending notifications some time before the various expiry events happen.
 *
 * @author ogronnesby
 */
public class CloudTrialExpirer extends ControllerMaintainer {
    private static final Logger log = Logger.getLogger(CloudTrialExpirer.class.getName());

    private static final Duration nonePlanAfter = Duration.ofDays(14);
    private static final Duration tombstoneAfter = Duration.ofDays(183);
    private final ListFlag<String> extendedTrialTenants;

    public CloudTrialExpirer(Controller controller, Duration interval) {
        super(controller, interval, null, SystemName.allOf(SystemName::isPublic));
        this.extendedTrialTenants = PermanentFlags.EXTENDED_TRIAL_TENANTS.bindTo(controller().flagSource());
    }

    @Override
    protected double maintain() {
        var a = tombstoneNonePlanTenants();
        var b = moveInactiveTenantsToNonePlan();
        return (a ? 0.5 : 0.0) + (b ? 0.5 : 0.0);
    }

    private boolean moveInactiveTenantsToNonePlan() {
        var idleTrialTenants = controller().tenants().asList().stream()
                .filter(this::tenantIsCloudTenant)
                .filter(this::tenantIsNotExemptFromExpiry)
                .filter(this::tenantHasNoDeployments)
                .filter(this::tenantHasTrialPlan)
                .filter(tenantReadersNotLoggedIn(nonePlanAfter))
                .toList();

        if (! idleTrialTenants.isEmpty()) {
            var tenants = idleTrialTenants.stream().map(Tenant::name).map(TenantName::value).collect(Collectors.joining(", "));
            log.info("Setting tenants to 'none' plan: " + tenants);
        }

        return setPlanNone(idleTrialTenants);
    }

    private boolean tombstoneNonePlanTenants() {
        var idleOldPlanTenants = controller().tenants().asList().stream()
                .filter(this::tenantIsCloudTenant)
                .filter(this::tenantIsNotExemptFromExpiry)
                .filter(this::tenantHasNoDeployments)
                .filter(this::tenantHasNonePlan)
                .filter(tenantReadersNotLoggedIn(tombstoneAfter))
                .toList();

        if (! idleOldPlanTenants.isEmpty()) {
            var tenants = idleOldPlanTenants.stream().map(Tenant::name).map(TenantName::value).collect(Collectors.joining(", "));
            log.info("Setting tenants as tombstoned: " + tenants);
        }

        return tombstoneTenants(idleOldPlanTenants);
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
        return !extendedTrialTenants.value().contains(tenant.name().value());
    }

    private boolean tenantHasNoDeployments(Tenant tenant) {
        return controller().applications().asList(tenant.name()).stream()
                .flatMap(app -> app.instances().values().stream())
                .mapToLong(instance -> instance.deployments().values().size())
                .sum() == 0;
    }

    private boolean setPlanNone(List<Tenant> tenants) {
        var success = true;
        for (var tenant : tenants) {
            try {
                controller().serviceRegistry().billingController().setPlan(tenant.name(), PlanId.from("none"), false, false);
            } catch (RuntimeException e) {
                log.info("Could not change plan for " + tenant.name() + ": " + e.getMessage());
                success = false;
            }
        }
        return success;
    }

    private boolean tombstoneTenants(List<Tenant> tenants) {
        var success = true;
        for (var tenant : tenants) {
            success &= deleteApplicationsWithNoDeployments(tenant);
            log.fine("Tombstoning empty tenant: " + tenant.name());
            try {
                controller().tenants().delete(tenant.name(), Optional.empty(), false);
            } catch (RuntimeException e) {
                log.info("Could not tombstone tenant " + tenant.name() + ": " + e.getMessage());
                success = false;
            }
        }
        return success;
    }

    private boolean deleteApplicationsWithNoDeployments(Tenant tenant) {
        // this method only removes applications with no active deployments in them
        var success = true;
        for (var application : controller().applications().asList(tenant.name())) {
            try {
                log.fine("Removing empty application: " + application.id());
                controller().applications().deleteApplication(application.id(), Optional.empty());
            } catch (RuntimeException e) {
                log.info("Could not removing application " + application.id() + ": " + e.getMessage());
                success = false;
            }
        }
        return success;
    }
}
