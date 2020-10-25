package com.yahoo.vespa.hosted.controller.application;

import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.api.integration.billing.Quota;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** Calculates the quota to allocate to a deployment. */
public class DeploymentQuotaCalculator {

    public static Quota calculate(Quota tenantQuota,
                                  List<Application> tenantApps,
                                  ApplicationId deployingApp, ZoneId deployingZone,
                                  DeploymentSpec deploymentSpec) {

        if (tenantQuota.budget().isEmpty()) return tenantQuota; // Shortcut if there is no budget limit to care about.

        if (deployingZone.environment().isProduction()) return probablyEnoughForAll(tenantQuota, tenantApps, deployingApp, deploymentSpec);

        return getMaximumAllowedQuota(tenantQuota, tenantApps, deployingApp, deployingZone);
    }

    /** Just get the maximum quota we are allowed to use. */
    private static Quota getMaximumAllowedQuota(Quota tenantQuota, List<Application> applications,
                                                ApplicationId application, ZoneId zone) {
        var usageOutsideDeployment = applications.stream()
                .map(app -> app.quotaUsage(application, zone))
                .reduce(QuotaUsage::add).orElse(QuotaUsage.none);
        return tenantQuota.subtractUsage(usageOutsideDeployment.rate());
    }

    /**
     * We want to avoid applying a resource change to an instance in production when it seems likely
     * that there will not be enough quota to apply this change to _all_ production instances.
     * <p>
     * To achieve this, we must make the assumption that all production instances will use
     * the same amount of resources, and so equally divide the quota among them.
     */
    private static Quota probablyEnoughForAll(Quota tenantQuota, List<Application> tenantApps,
                                              ApplicationId application, DeploymentSpec deploymentSpec) {

        TenantAndApplicationId deployingApp = TenantAndApplicationId.from(application);

        var usageOutsideApplication = tenantApps.stream()
                .filter(app -> !app.id().equals(deployingApp))
                .map(Application::quotaUsage).reduce(QuotaUsage::add).orElse(QuotaUsage.none);

        long productionInstances = deploymentSpec.instances().stream()
                .filter(instance -> instance.concerns(Environment.prod))
                .count();

        return tenantQuota.withBudget(
                tenantQuota.subtractUsage(usageOutsideApplication.rate())
                        .budget().get().divide(BigDecimal.valueOf(productionInstances),
                        5, RoundingMode.HALF_UP)); // 1/1000th of a cent should be accurate enough
    }
}
