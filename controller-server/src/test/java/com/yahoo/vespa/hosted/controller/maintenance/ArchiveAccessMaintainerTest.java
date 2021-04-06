// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.flags.InMemoryFlagSource;
import com.yahoo.vespa.flags.PermanentFlags;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.api.integration.archive.ArchiveBucket;
import com.yahoo.vespa.hosted.controller.api.integration.archive.MockArchiveService;
import com.yahoo.vespa.hosted.controller.api.role.Role;
import com.yahoo.vespa.hosted.controller.restapi.ContainerTester;
import com.yahoo.vespa.hosted.controller.restapi.ControllerContainerCloudTest;
import com.yahoo.vespa.hosted.controller.tenant.Tenant;
import org.junit.Test;

import java.time.Duration;
import java.util.Map;

import static com.yahoo.application.container.handler.Request.Method.PUT;
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
        var tenant1 = tester.createTenant("tenant1", Tenant.Type.cloud);
        var tenant2 = tester.createTenant("tenant2", Tenant.Type.cloud);

        containerTester.assertResponse(request("/application/v4/tenant/tenant1/archive-access", PUT)
                        .data("{\"role\":\"arn:aws:iam::123456789012:role/my-role\"}").roles(Role.administrator(tenant1)),
                "{\"message\":\"Archive access role set to 'arn:aws:iam::123456789012:role/my-role' for tenant tenant1.\"}", 200);

        containerTester.assertResponse(request("/application/v4/tenant/tenant2/archive-access", PUT)
                        .data("{\"role\":\"arn:aws:iam::210987654321:role/their-role\"}").roles(Role.administrator(tenant2)),
                "{\"message\":\"Archive access role set to 'arn:aws:iam::210987654321:role/their-role' for tenant tenant2.\"}", 200);

        tester.controller().archiveBucketDb().archiveUriFor(ZoneId.from("prod.us-east-3"), tenant1);
        var testBucket = new ArchiveBucket("bucketArn", "keyArn").withTenant(tenant1);

        MockArchiveService archiveService = (MockArchiveService) tester.controller().serviceRegistry().archiveService();
        assertNull(archiveService.authorizedIamRoles.get(testBucket));
        new ArchiveAccessMaintainer(containerTester.controller(), Duration.ofMinutes(10)).maintain();
        assertEquals(Map.of(tenant1, "arn:aws:iam::123456789012:role/my-role"), archiveService.authorizedIamRoles.get(testBucket));
    }
}
