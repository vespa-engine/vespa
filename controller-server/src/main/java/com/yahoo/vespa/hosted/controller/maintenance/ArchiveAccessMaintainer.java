// Copyright 2021 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root
package com.yahoo.vespa.hosted.controller.maintenance;

import com.google.common.collect.Maps;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.archive.ArchiveService;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneRegistry;
import com.yahoo.vespa.hosted.controller.archive.CuratorArchiveBucketDb;
import com.yahoo.vespa.hosted.controller.tenant.CloudTenant;
import com.yahoo.vespa.hosted.controller.tenant.Tenant;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Update archive access permissions with roles from tenants
 *
 * @author andreer
 */
public class ArchiveAccessMaintainer extends ControllerMaintainer {

    private static final String bucketCountMetricName = "archive.bucketCount";

    private final CuratorArchiveBucketDb archiveBucketDb;
    private final ArchiveService archiveService;
    private final ZoneRegistry zoneRegistry;
    private final Metric metric;

    public ArchiveAccessMaintainer(Controller controller, Metric metric, Duration interval) {
        super(controller, interval);
        this.archiveBucketDb = controller.archiveBucketDb();
        this.archiveService = controller.serviceRegistry().archiveService();
        this.zoneRegistry = controller().zoneRegistry();
        this.metric = metric;
    }

    @Override
    protected double maintain() {
        // Count buckets - so we can alert if we get close to the account limit of 1000
        zoneRegistry.zones().all().ids().forEach(zoneId ->
                metric.set(bucketCountMetricName, archiveBucketDb.buckets(zoneId).size(),
                        metric.createContext(Map.of("zone", zoneId.value()))));


        zoneRegistry.zones().controllerUpgraded().zones().forEach(z -> {
                    ZoneId zoneId = z.getId();
                    try {
                        var tenantArchiveAccessRoles = cloudTenantArchiveExternalAccessRoles();
                        archiveBucketDb.buckets(zoneId).forEach(archiveBucket ->
                                archiveService.updateBucketAndKeyPolicy(zoneId, archiveBucket,
                                        Maps.filterEntries(tenantArchiveAccessRoles,
                                                entry -> archiveBucket.tenants().contains(entry.getKey())))
                        );
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to maintain archive access in " + zoneId.value(), e);
                    }
                }
        );

        return 1.0;
    }

    private Map<TenantName, String> cloudTenantArchiveExternalAccessRoles() {
        List<Tenant> tenants = controller().tenants().asList();
        return tenants.stream()
                .filter(t -> t instanceof CloudTenant)
                .map(t -> (CloudTenant) t)
                .filter(t -> t.archiveAccessRole().isPresent())
                .collect(Collectors.toUnmodifiableMap(
                        Tenant::name, cloudTenant -> cloudTenant.archiveAccessRole().orElseThrow()));
    }

}
