// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.archive;

import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.api.integration.archive.ArchiveBuckets;
import com.yahoo.vespa.hosted.controller.api.integration.archive.MockArchiveService;
import com.yahoo.vespa.hosted.controller.api.integration.archive.VespaManagedArchiveBucket;
import org.apache.curator.shaded.com.google.common.collect.Streams;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CuratorArchiveBucketDbTest {

    @Test
    void archiveUriForTenant() {
        ControllerTester tester = new ControllerTester(SystemName.Public);
        CuratorArchiveBucketDb bucketDb = new CuratorArchiveBucketDb(tester.controller());

        tester.curator().writeArchiveBuckets(ZoneId.defaultId(),
                ArchiveBuckets.EMPTY.with(new VespaManagedArchiveBucket("existingBucket", "keyArn").withTenant(TenantName.defaultName())));

        // Finds existing bucket in db
        assertEquals(Optional.of(URI.create("s3://existingBucket/")), bucketDb.archiveUriFor(ZoneId.defaultId(), TenantName.defaultName(), true));

        // Assigns to existing bucket while there is space
        IntStream.range(0, 4).forEach(i ->
                assertEquals(
                        Optional.of(URI.create("s3://existingBucket/")), bucketDb
                                .archiveUriFor(ZoneId.defaultId(), TenantName.from("tenant" + i), true)));

        // Creates new bucket when existing buckets are full
        assertEquals(Optional.of(URI.create("s3://bucketName/")), bucketDb.archiveUriFor(ZoneId.defaultId(), TenantName.from("lastDrop"), true));

        // Creates new bucket when there are no existing buckets in zone
        assertEquals(Optional.of(URI.create("s3://bucketName/")), bucketDb.archiveUriFor(ZoneId.from("prod.us-east-3"), TenantName.from("firstInZone"), true));

        // Does not create bucket if not required
        assertEquals(Optional.empty(), bucketDb.archiveUriFor(ZoneId.from("prod.us-east-3"), TenantName.from("newTenant"), false));

        // Lists all buckets by zone
        Set<TenantName> existingBucketTenants = Streams.concat(Stream.of(TenantName.defaultName()), IntStream.range(0, 4).mapToObj(i -> TenantName.from("tenant" + i))).collect(Collectors.toUnmodifiableSet());
        assertEquals(
                Set.of(
                        new VespaManagedArchiveBucket("existingBucket", "keyArn").withTenants(existingBucketTenants),
                        new VespaManagedArchiveBucket("bucketName", "keyArn").withTenant(TenantName.from("lastDrop"))),
                bucketDb.buckets(ZoneId.defaultId()).vespaManaged());
        assertEquals(
                Set.of(new VespaManagedArchiveBucket("bucketName", "keyArn").withTenant(TenantName.from("firstInZone"))),
                bucketDb.buckets(ZoneId.from("prod.us-east-3")).vespaManaged());
    }

    @Test
    void archiveUriForAccount() {
        Controller controller = new ControllerTester(SystemName.Public).controller();
        CuratorArchiveBucketDb bucketDb = new CuratorArchiveBucketDb(controller);
        MockArchiveService service = (MockArchiveService) controller.serviceRegistry().archiveService();
        ManualClock clock = (ManualClock) controller.clock();

        CloudAccount acc1 = CloudAccount.from("001122334455");
        ZoneId z1 = ZoneId.from("prod.us-east-3");

        assertEquals(Optional.empty(), bucketDb.archiveUriFor(z1, acc1, true)); // Initially not set
        service.setEnclaveArchiveBucket(z1, acc1, "bucket-1");
        assertEquals(Optional.empty(), bucketDb.archiveUriFor(z1, acc1, false));
        assertEquals(Optional.of(URI.create("s3://bucket-1/")), bucketDb.archiveUriFor(z1, acc1, true));
        assertEquals(Optional.of(URI.create("s3://bucket-1/")), bucketDb.archiveUriFor(z1, acc1, false));

        service.setEnclaveArchiveBucket(z1, acc1, "bucket-2");
        assertEquals(Optional.of(URI.create("s3://bucket-1/")), bucketDb.archiveUriFor(z1, acc1, true)); // Returns old value even with search

        clock.advance(Duration.ofMinutes(61)); // After expiry the cache is expired, new search is performed
        assertEquals(Optional.of(URI.create("s3://bucket-1/")), bucketDb.archiveUriFor(z1, acc1, false)); // When requesting without search, return previous value even if expired
        assertEquals(Optional.of(URI.create("s3://bucket-2/")), bucketDb.archiveUriFor(z1, acc1, true));
        assertEquals(Optional.of(URI.create("s3://bucket-2/")), bucketDb.archiveUriFor(z1, acc1, false));
    }
}
