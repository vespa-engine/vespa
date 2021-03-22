// Copyright 2021 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.archive.ArchiveBucketDb;
import com.yahoo.vespa.hosted.controller.api.integration.archive.ArchiveService;
import com.yahoo.vespa.hosted.controller.tenant.CloudTenant;
import com.yahoo.vespa.hosted.controller.tenant.Tenant;

import java.time.Duration;
import java.util.stream.Collectors;

/**
 * Update archive access permissions with roles from tenants
 */
public class ArchiveAccessMaintainer extends ControllerMaintainer {

    // TODO archive: Add to ControllerMaintenance, so it actually runs

    private final ArchiveBucketDb archiveBucketDb;
    private final ArchiveService archiveService;

    public ArchiveAccessMaintainer(Controller controller, Duration interval, ArchiveBucketDb archiveBucketDb) {
        super(controller, interval);
        this.archiveBucketDb = archiveBucketDb;
        this.archiveService = controller.serviceRegistry().archiveService();
    }

    @Override
    protected boolean maintain() {
        var tenantArchiveAccessRoles = controller().tenants().asList().stream()
                .filter(t -> t instanceof CloudTenant)
                .map(t -> (CloudTenant) t)
                .filter(t -> t.archiveAccessRole().isPresent())
                .collect(Collectors.toUnmodifiableMap(
                        Tenant::name, cloudTenant -> cloudTenant.archiveAccessRole().orElseThrow()));

        archiveBucketDb.zoneBuckets().forEach(((zoneId, bucketName) ->
                archiveService.updateBucketAndKeyPolicy(zoneId, bucketName, tenantArchiveAccessRoles)));

        return true;
    }
}
