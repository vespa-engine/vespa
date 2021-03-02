// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.api.integration.archive.MockArchiveService;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.NodeRepository;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentContext;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTester;
import org.junit.Test;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

/**
 * @author freva
 */
public class ArchiveUriUpdaterTest {

    private final DeploymentTester tester = new DeploymentTester();

    @Test
    public void archive_uri_test() {
        var updater = new ArchiveUriUpdater(tester.controller(), Duration.ofDays(1));

        var tenant1 = TenantName.from("tenant1");
        var tenant2 = TenantName.from("tenant2");
        var tenantInfra = TenantName.from("hosted-vespa");
        var application = tester.newDeploymentContext(tenant1.value(), "app1", "instance1");
        ZoneId zone = ZoneId.from("prod", "ap-northeast-1");

        // Initially we should not set any archive URIs as the archive service does not return any
        updater.maintain();
        assertArchiveUris(Map.of(), zone);

        // Archive service now has URI for tenant1, but tenant1 is not deployed in zone
        setArchiveUriInService(Map.of(tenant1, "uri-1"), zone);
        setArchiveUriInService(Map.of(tenantInfra, "uri-3"), zone);
        updater.maintain();
        assertArchiveUris(Map.of(), zone);

        deploy(application, zone);
        updater.maintain();
        assertArchiveUris(Map.of(tenant1, "uri-1", tenantInfra, "uri-3"), zone);

        // URI for tenant1 should be updated and removed for tenant2
        setArchiveUriInNodeRepo(Map.of(tenant1, "wrong-uri", tenant2, "uri-2"), zone);
        updater.maintain();
        assertArchiveUris(Map.of(tenant1, "uri-1", tenantInfra, "uri-3"), zone);
    }

    private void assertArchiveUris(Map<TenantName, String> expectedUris, ZoneId zone) {
        Map<TenantName, String> actualUris = tester.controller().serviceRegistry().configServer().nodeRepository()
                .getArchiveUris(zone).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().toString()));
        assertEquals(expectedUris, actualUris);
    }

    private void setArchiveUriInService(Map<TenantName, String> archiveUris, ZoneId zone) {
        MockArchiveService archiveService = (MockArchiveService) tester.controller().serviceRegistry().archiveService();
        archiveUris.forEach((tenant, uri) -> archiveService.setArchiveUri(zone, tenant, URI.create(uri)));
    }

    private void setArchiveUriInNodeRepo(Map<TenantName, String> archiveUris, ZoneId zone) {
        NodeRepository nodeRepository = tester.controller().serviceRegistry().configServer().nodeRepository();
        archiveUris.forEach((tenant, uri) -> nodeRepository.setArchiveUri(zone, tenant, URI.create(uri)));
    }

    private void deploy(DeploymentContext application, ZoneId zone) {
        application.runJob(JobType.from(SystemName.main, zone).orElseThrow(), new ApplicationPackage(new byte[0]), Version.fromString("7.1"));
    }
}