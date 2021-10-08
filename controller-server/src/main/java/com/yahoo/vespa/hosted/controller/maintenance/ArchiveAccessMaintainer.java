// Copyright 2021 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root
package com.yahoo.vespa.hosted.controller.maintenance;

import com.google.common.collect.Maps;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.zone.ZoneApi;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.flags.BooleanFlag;
import com.yahoo.vespa.flags.FetchVector;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.archive.ArchiveService;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneRegistry;
import com.yahoo.vespa.hosted.controller.archive.CuratorArchiveBucketDb;
import com.yahoo.vespa.hosted.controller.tenant.AthenzTenant;
import com.yahoo.vespa.hosted.controller.tenant.CloudTenant;
import com.yahoo.vespa.hosted.controller.tenant.Tenant;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
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
    private final BooleanFlag archiveEnabled;
    private final BooleanFlag developerRoleEnabled;

    public ArchiveAccessMaintainer(Controller controller, Metric metric, Duration interval) {
        super(controller, interval);
        this.archiveBucketDb = controller.archiveBucketDb();
        this.archiveService = controller.serviceRegistry().archiveService();
        this.zoneRegistry = controller().zoneRegistry();
        this.metric = metric;
        this.archiveEnabled = Flags.ENABLE_ONPREM_TENANT_S3_ARCHIVE.bindTo(controller().flagSource());
        this.developerRoleEnabled = Flags.ENABLE_TENANT_DEVELOPER_ROLE.bindTo(controller().flagSource());
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
                        var tenantArchiveAccessRoles = tenantArchiveAccessRoles(z);
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

    private Map<TenantName, String> tenantArchiveAccessRoles(ZoneApi zone) {
        List<Tenant> tenants = controller().tenants().asList();
        if (zoneRegistry.system().isPublic()) {
            return tenants.stream()
                    .filter(t -> t instanceof CloudTenant)
                    .map(t -> (CloudTenant) t)
                    .filter(t -> t.archiveAccessRole().isPresent())
                    .collect(Collectors.toUnmodifiableMap(
                            Tenant::name, cloudTenant -> cloudTenant.archiveAccessRole().orElseThrow()));
        } else {
            return tenants.stream()
                    .filter(t -> t instanceof AthenzTenant
                            && enabled(archiveEnabled, t, zone) && enabled(developerRoleEnabled, t, zone))
                    .map(Tenant::name)
                    .collect(Collectors.toUnmodifiableMap(
                            Function.identity(), t -> zoneRegistry.tenantDeveloperRoleArn(t).orElseThrow()));

        }
    }

    private boolean enabled(BooleanFlag flag, Tenant tenant, ZoneApi zone) {
        return flag.with(FetchVector.Dimension.TENANT_ID, tenant.name().value())
                .with(FetchVector.Dimension.ZONE_ID, zone.getId().value())
                .value();
    }

}
