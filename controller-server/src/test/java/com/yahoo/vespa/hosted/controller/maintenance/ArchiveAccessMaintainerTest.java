// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.jdisc.test.MockMetric;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.flags.InMemoryFlagSource;
import com.yahoo.vespa.flags.PermanentFlags;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.LockedTenant;
import com.yahoo.vespa.hosted.controller.api.integration.archive.ArchiveBucket;
import com.yahoo.vespa.hosted.controller.api.integration.archive.MockArchiveService;
import com.yahoo.vespa.hosted.controller.restapi.ContainerTester;
import com.yahoo.vespa.hosted.controller.restapi.ControllerContainerCloudTest;
import com.yahoo.vespa.hosted.controller.tenant.Tenant;
import org.junit.Test;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * @author andreer
 */
public class ArchiveAccessMaintainerTest extends ControllerContainerCloudTest {

    @Test
    public void grantsRoleAccess() {
        var containerTester = new ContainerTester(container, "");
        ((InMemoryFlagSource) containerTester.controller().flagSource())
                .withBooleanFlag(PermanentFlags.ENABLE_PUBLIC_SIGNUP_FLOW.id(), true)
                .withStringFlag(Flags.SYNC_HOST_LOGS_TO_S3_BUCKET.id(), "auto");
        var tester = new ControllerTester(containerTester);

        String tenant1role = "arn:aws:iam::123456789012:role/my-role";
        String tenant2role = "arn:aws:iam::210987654321:role/my-role";
        var tenant1 = createTenantWithAccessRole(tester, "tenant1", tenant1role);
        createTenantWithAccessRole(tester, "tenant2", tenant2role);

        tester.controller().archiveBucketDb().archiveUriFor(ZoneId.from("prod.us-east-3"), tenant1);
        var testBucket = new ArchiveBucket("bucketName", "keyArn").withTenant(tenant1);

        MockArchiveService archiveService = (MockArchiveService) tester.controller().serviceRegistry().archiveService();
        assertNull(archiveService.authorizedIamRoles.get(testBucket));
        MockMetric metric = new MockMetric();
        new ArchiveAccessMaintainer(containerTester.controller(), metric, Duration.ofMinutes(10)).maintain();
        assertEquals(Map.of(tenant1, tenant1role), archiveService.authorizedIamRoles.get(testBucket));
        assertEquals(Map.of("archive.bucketCount", Map.of(Map.of(), 1d)), metric.metrics());
    }

    private TenantName createTenantWithAccessRole(ControllerTester tester, String tenantName, String role) {
        var tenant = tester.createTenant(tenantName, Tenant.Type.cloud);
        tester.controller().tenants().lockOrThrow(tenant, LockedTenant.Cloud.class, lockedTenant -> {
            lockedTenant = lockedTenant.withArchiveAccessRole(Optional.of(role));
            tester.controller().tenants().store(lockedTenant);
        });
        return tenant;
    }
}
