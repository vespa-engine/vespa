// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.application;

import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.api.integration.billing.Quota;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Calculates the quota to allocate to a deployment.
 *
 * @author ogronnesby
 * @author andreer
 */
public class DeploymentQuotaCalculator {

    public static Quota calculate(Quota tenantQuota,
                                  List<Application> tenantApps,
                                  ApplicationId deployingApp, ZoneId deployingZone,
                                  DeploymentSpec deploymentSpec)
    {
        if (tenantQuota.budget().isEmpty()) return tenantQuota; // Shortcut if there is no budget limit to care about.
        if (deployingZone.environment().isTest()) return tenantQuota;
        if (deployingZone.environment().isProduction()) return probablyEnoughForAll(tenantQuota, tenantApps, deployingApp, deploymentSpec);
        return getMaximumAllowedQuota(tenantQuota, tenantApps, deployingApp, deployingZone);
    }

    public static QuotaUsage calculateQuotaUsage(com.yahoo.vespa.hosted.controller.api.integration.configserver.Application application) {
        // the .max() resources are only specified when the user has specified a max.  to make sure we enforce quotas
        // correctly we retrieve the maximum of .current() and .max() - otherwise we would keep adding 0s for those
        // that are not using autoscaling.
        var quotaUsageRate = application.clusters().values().stream()
                .filter(cluster -> ! cluster.type().equals(ClusterSpec.Type.admin))
                .map(cluster -> largestQuotaUsage(cluster.current(), cluster.max()))
                .mapToDouble(resources -> resources.nodes() * resources.nodeResources().cost())
                .sum();
        return QuotaUsage.create(quotaUsageRate);
    }

    private static ClusterResources largestQuotaUsage(ClusterResources a, ClusterResources b) {
        var usageA = a.nodes() * a.nodeResources().cost();
        var usageB = b.nodes() * b.nodeResources().cost();
        return usageA < usageB ? b : a;
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

        long productionDeployments = Math.max(1, deploymentSpec.instances().stream()
                .flatMap(instance -> instance.zones().stream())
                .filter(zone -> zone.environment().isProduction())
                .count());

        return tenantQuota.withBudget(
                tenantQuota.subtractUsage(usageOutsideApplication.rate())
                        .budget().get().divide(BigDecimal.valueOf(productionDeployments),
                        5, RoundingMode.HALF_UP)); // 1/1000th of a cent should be accurate enough
    }
}
