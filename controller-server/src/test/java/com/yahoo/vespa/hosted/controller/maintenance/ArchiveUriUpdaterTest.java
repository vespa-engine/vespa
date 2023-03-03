// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.flags.InMemoryFlagSource;
import com.yahoo.vespa.flags.PermanentFlags;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.api.integration.archive.ArchiveBuckets;
import com.yahoo.vespa.hosted.controller.api.integration.archive.ArchiveUriUpdate;
import com.yahoo.vespa.hosted.controller.api.integration.archive.MockArchiveService;
import com.yahoo.vespa.hosted.controller.api.integration.archive.VespaManagedArchiveBucket;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ArchiveUris;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.NodeRepository;
import com.yahoo.vespa.hosted.controller.application.SystemApplication;
import com.yahoo.vespa.hosted.controller.deployment.ApplicationPackageBuilder;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentContext;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTester;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.List;
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
        var account1 = CloudAccount.from("001122334455");
        var tenantInfra = SystemApplication.TENANT;
        ZoneId zone = ZoneId.from("prod", "aws-us-east-1c");

        // Initially we should only is the bucket for hosted-vespa tenant
        updater.maintain();
        assertArchiveUris(zone, Map.of(TenantName.from("hosted-vespa"), "s3://bucketName/"), Map.of());
        assertArchiveUris(ZoneId.from("prod", "controller"), Map.of(TenantName.from("hosted-vespa"), "s3://bucketName/"), Map.of());

        // Archive service now has URI for tenant1, but tenant1 is not deployed in zone
        setBucketNameInService(Map.of(tenant2, "uri-1"), zone);
        setAccountBucketNameInService(zone, account1, "bkt-1");
        updater.maintain();
        assertArchiveUris(zone, Map.of(TenantName.from("hosted-vespa"), "s3://bucketName/"), Map.of());

        ((InMemoryFlagSource) tester.controller().flagSource())
                .withListFlag(PermanentFlags.CLOUD_ACCOUNTS.id(), List.of(account1.value()), String.class);
        deploy(tester.newDeploymentContext(tenant1.value(), "app1", "instance1"), zone, account1);
        deploy(tester.newDeploymentContext(tenant2.value(), "app1", "instance1"), zone, CloudAccount.empty);

        updater.maintain();
        assertArchiveUris(zone, Map.of(tenant2, "s3://uri-1/", tenantInfra, "s3://bucketName/"), Map.of(account1, "s3://bkt-1/"));

        // URI for tenant1 should be updated and removed for tenant2
        setArchiveUriInNodeRepo(Map.of(tenant1, "wrong-uri", tenant2, "uri-2"), zone);
        updater.maintain();
        assertArchiveUris(zone, Map.of(tenant2, "s3://uri-1/", tenantInfra, "s3://bucketName/"), Map.of(account1, "s3://bkt-1/"));
    }

    private void assertArchiveUris(ZoneId zone, Map<TenantName, String> expectedTenantUris, Map<CloudAccount, String> expectedAccountUris) {
        ArchiveUris archiveUris = tester.controller().serviceRegistry().configServer().nodeRepository().getArchiveUris(zone);
        assertEquals(expectedTenantUris, archiveUris.tenantArchiveUris().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().toString())));
        assertEquals(expectedAccountUris, archiveUris.accountArchiveUris().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().toString())));
    }

    private void setBucketNameInService(Map<TenantName, String> bucketNames, ZoneId zone) {
        ArchiveBuckets buckets = tester.controller().curator().readArchiveBuckets(zone);
        for (var entry : bucketNames.entrySet())
            buckets = buckets.with(new VespaManagedArchiveBucket(entry.getValue(), "keyArn").withTenant(entry.getKey()));
        tester.controller().curator().writeArchiveBuckets(zone, buckets);
    }

    private void setAccountBucketNameInService(ZoneId zone, CloudAccount cloudAccount, String bucketName) {
        ((MockArchiveService) tester.controller().serviceRegistry().archiveService()).setEnclaveArchiveBucket(zone, cloudAccount, bucketName);
    }

    private void setArchiveUriInNodeRepo(Map<TenantName, String> archiveUris, ZoneId zone) {
        NodeRepository nodeRepository = tester.controller().serviceRegistry().configServer().nodeRepository();
        archiveUris.forEach((tenant, uri) -> nodeRepository.updateArchiveUri(zone, ArchiveUriUpdate.setArchiveUriFor(tenant, URI.create(uri))));
    }

    private void deploy(DeploymentContext application, ZoneId zone, CloudAccount cloudAccount) {
        application.submit(new ApplicationPackageBuilder()
                .cloudAccount(cloudAccount.value())
                .region(zone.region().value())
                .build()).deploy();
    }

}
