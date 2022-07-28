// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.archive;

import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.api.integration.archive.ArchiveBucket;
import org.apache.curator.shaded.com.google.common.collect.Streams;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CuratorArchiveBucketDbTest {

    @Test
    void archiveUriFor() {
        ControllerTester tester = new ControllerTester(SystemName.Public);
        CuratorArchiveBucketDb bucketDb = new CuratorArchiveBucketDb(tester.controller());

        tester.curator().writeArchiveBuckets(ZoneId.defaultId(),
                Set.of(new ArchiveBucket("existingBucket", "keyArn").withTenant(TenantName.defaultName())));

        // Finds existing bucket in db
        assertEquals(Optional.of(URI.create("s3://existingBucket/default/")), bucketDb.archiveUriFor(ZoneId.defaultId(), TenantName.defaultName(), true));

        // Assigns to existing bucket while there is space
        IntStream.range(0, 4).forEach(i ->
                assertEquals(
                        Optional.of(URI.create("s3://existingBucket/tenant" + i + "/")), bucketDb
                                .archiveUriFor(ZoneId.defaultId(), TenantName.from("tenant" + i), true)));

        // Creates new bucket when existing buckets are full
        assertEquals(Optional.of(URI.create("s3://bucketName/lastDrop/")), bucketDb.archiveUriFor(ZoneId.defaultId(), TenantName.from("lastDrop"), true));

        // Creates new bucket when there are no existing buckets in zone
        assertEquals(Optional.of(URI.create("s3://bucketName/firstInZone/")), bucketDb.archiveUriFor(ZoneId.from("prod.us-east-3"), TenantName.from("firstInZone"), true));

        // Does not create bucket if not required
        assertEquals(Optional.empty(), bucketDb.archiveUriFor(ZoneId.from("prod.us-east-3"), TenantName.from("newTenant"), false));

        // Lists all buckets by zone
        Set<TenantName> existingBucketTenants = Streams.concat(Stream.of(TenantName.defaultName()), IntStream.range(0, 4).mapToObj(i -> TenantName.from("tenant" + i))).collect(Collectors.toUnmodifiableSet());
        assertEquals(
                Set.of(
                        new ArchiveBucket("existingBucket", "keyArn").withTenants(existingBucketTenants),
                        new ArchiveBucket("bucketName", "keyArn").withTenant(TenantName.from("lastDrop"))),
                bucketDb.buckets(ZoneId.defaultId()));
        assertEquals(
                Set.of(new ArchiveBucket("bucketName", "keyArn").withTenant(TenantName.from("firstInZone"))),
                bucketDb.buckets(ZoneId.from("prod.us-east-3")));
    }
}
