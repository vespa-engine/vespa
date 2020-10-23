package com.yahoo.vespa.hosted.controller.application;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.api.integration.billing.Quota;

import java.util.List;

public class DeploymentQuotaCalculator {

    public static Quota calculate(Quota tenantQuota, List<Application> applications, ApplicationId application, ZoneId zone) {

        var tenantUsage = applications.stream()
                .map(a -> a.quotaUsageExcluding(application, zone))
                .reduce(QuotaUsage::add).orElse(QuotaUsage.none);

        return tenantQuota.subtractUsage(tenantUsage.rate());
    }
}
