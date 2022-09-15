// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.api.integration.archive.ArchiveBucket;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.NodeRepository;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.application.SystemApplication;
import com.yahoo.vespa.hosted.controller.application.pkg.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentContext;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTester;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author freva
 */
public class ArchiveUriUpdaterTest {

    private final DeploymentTester tester = new DeploymentTester(new ControllerTester(SystemName.Public));

    @Test
    void archive_uri_test() {
        var updater = new ArchiveUriUpdater(tester.controller(), Duration.ofDays(1));

        var tenant1 = TenantName.from("tenant1");
        var tenant2 = TenantName.from("tenant2");
        var tenantInfra = SystemApplication.TENANT;
        var application = tester.newDeploymentContext(tenant1.value(), "app1", "instance1");
        ZoneId zone = ZoneId.from("prod", "aws-us-east-1c");

        // Initially we should only is the bucket for hosted-vespa tenant
        updater.maintain();
        assertArchiveUris(Map.of(TenantName.from("hosted-vespa"), "s3://bucketName/hosted-vespa/"), zone);
        assertArchiveUris(Map.of(TenantName.from("hosted-vespa"), "s3://bucketName/hosted-vespa/"), ZoneId.from("prod", "controller"));

        // Archive service now has URI for tenant1, but tenant1 is not deployed in zone
        setBucketNameInService(Map.of(tenant1, "uri-1"), zone);
        updater.maintain();
        assertArchiveUris(Map.of(TenantName.from("hosted-vespa"), "s3://bucketName/hosted-vespa/"), zone);

        deploy(application, zone);
        updater.maintain();
        assertArchiveUris(Map.of(tenant1, "s3://uri-1/tenant1/", tenantInfra, "s3://bucketName/hosted-vespa/"), zone);

        // URI for tenant1 should be updated and removed for tenant2
        setArchiveUriInNodeRepo(Map.of(tenant1, "wrong-uri", tenant2, "uri-2"), zone);
        updater.maintain();
        assertArchiveUris(Map.of(tenant1, "s3://uri-1/tenant1/", tenantInfra, "s3://bucketName/hosted-vespa/"), zone);
    }

    private void assertArchiveUris(Map<TenantName, String> expectedUris, ZoneId zone) {
        Map<TenantName, String> actualUris = tester.controller().serviceRegistry().configServer().nodeRepository()
                .getArchiveUris(zone).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().toString()));
        assertEquals(expectedUris, actualUris);
    }

    private void setBucketNameInService(Map<TenantName, String> bucketNames, ZoneId zone) {
        var archiveBuckets = new LinkedHashSet<>(tester.controller().curator().readArchiveBuckets(zone));
        bucketNames.forEach((tenantName, bucketName) ->
                archiveBuckets.add(new ArchiveBucket(bucketName, "keyArn").withTenant(tenantName)));
        tester.controller().curator().writeArchiveBuckets(zone, archiveBuckets);
    }

    private void setArchiveUriInNodeRepo(Map<TenantName, String> archiveUris, ZoneId zone) {
        NodeRepository nodeRepository = tester.controller().serviceRegistry().configServer().nodeRepository();
        archiveUris.forEach((tenant, uri) -> nodeRepository.setArchiveUri(zone, tenant, URI.create(uri)));
    }

    private void deploy(DeploymentContext application, ZoneId zone) {
        application.runJob(JobType.deploymentTo(zone), new ApplicationPackage(new byte[0]));
    }

}
