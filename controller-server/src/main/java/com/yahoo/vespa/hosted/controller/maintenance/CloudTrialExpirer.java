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
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Expires unused tenants from Vespa Cloud.
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
        var expiredTenants = controller().tenants().asList().stream()
                .filter(this::tenantIsCloudTenant)           // only valid for cloud tenants
                .filter(this::tenantHasTrialPlan)            // only valid to expire actual trial tenants
                .filter(this::tenantIsNotExemptFromExpiry)   // feature flag might exempt tenant from expiry
                .filter(this::tenantReadersNotLoggedIn)      // no user logged in last 14 days
                .filter(this::tenantHasNoDeployments)        // no running deployments active
                .collect(Collectors.toList());

        if (! expiredTenants.isEmpty()) {
            var expiredTenantNames = expiredTenants.stream()
                    .map(Tenant::name)
                    .map(TenantName::value)
                    .collect(Collectors.joining(", "));

            log.info("Moving expired tenants to 'none' plan: " + expiredTenantNames);
        }

        expireTenants(expiredTenants);

        return 1;
    }

    private boolean tenantIsCloudTenant(Tenant tenant) {
        return tenant.type() == Tenant.Type.cloud;
    }

    private boolean tenantReadersNotLoggedIn(Tenant tenant) {
        return tenant.lastLoginInfo().get(LastLoginInfo.UserLevel.user)
                .map(instant -> {
                    var sinceLastLogin = Duration.between(instant, controller().clock().instant());
                    return sinceLastLogin.compareTo(loginExpiry) > 0;
                })
                .orElse(false);
    }

    private boolean tenantHasTrialPlan(Tenant tenant) {
        var planId = controller().serviceRegistry().billingController().getPlan(tenant.name());
        return "trial".equals(planId.value());
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

    private void expireTenants(List<Tenant> tenants) {
        tenants.forEach(tenant -> {
            controller().serviceRegistry().billingController().setPlan(tenant.name(), PlanId.from("none"), false);
        });
    }
}
