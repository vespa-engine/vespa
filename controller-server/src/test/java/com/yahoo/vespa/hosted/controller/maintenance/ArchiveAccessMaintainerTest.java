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
import java.util.Set;

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
        var tenantName = tester.createTenant("tenant1", Tenant.Type.cloud);
        containerTester.assertResponse(request("/application/v4/tenant/tenant1/archive-access", PUT)
                        .data("{\"role\":\"arn:aws:iam::123456789012:role/my-role\"}").roles(Role.administrator(tenantName)),
                "{\"message\":\"Archive access role set to 'arn:aws:iam::123456789012:role/my-role' for tenant tenant1.\"}", 200);

        tester.controller().archiveBucketDb().archiveUriFor(ZoneId.from("prod.us-east-3"), tenantName);
        var testBucket = new ArchiveBucket("bucketArn", "keyArn", Set.of(tenantName));

        MockArchiveService archiveService = (MockArchiveService) tester.controller().serviceRegistry().archiveService();
        assertNull(archiveService.authorizedIamRoles.get(testBucket));
        new ArchiveAccessMaintainer(containerTester.controller(), Duration.ofMinutes(10)).maintain();
        assertEquals("arn:aws:iam::123456789012:role/my-role", archiveService.authorizedIamRoles.get(testBucket).get(tenantName));
    }
}
