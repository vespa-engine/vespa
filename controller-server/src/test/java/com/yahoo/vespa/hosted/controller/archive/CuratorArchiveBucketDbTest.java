package com.yahoo.vespa.hosted.controller.archive;

import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.flags.InMemoryFlagSource;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.api.integration.archive.ArchiveBucket;
import org.apache.curator.shaded.com.google.common.collect.Streams;
import org.junit.Test;

import java.net.URI;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.Assert.*;

public class CuratorArchiveBucketDbTest {

    @Test
    public void archiveUriFor() {
        ControllerTester tester = new ControllerTester();
        InMemoryFlagSource flagSource = (InMemoryFlagSource) tester.controller().flagSource();
        CuratorArchiveBucketDb bucketDb = new CuratorArchiveBucketDb(tester.controller());

        tester.curator().writeArchiveBuckets(ZoneId.defaultId(),
                Set.of(new ArchiveBucket("existingBucket", "keyArn").withTenant(TenantName.defaultName())));

        // Nothing when feature flag is not set.
        assertEquals(Optional.empty(), bucketDb.archiveUriFor(ZoneId.defaultId(), TenantName.defaultName()));

        // Returns hardcoded name from feature flag
        flagSource.withStringFlag(Flags.SYNC_HOST_LOGS_TO_S3_BUCKET.id(), "hardcoded");
        assertEquals(Optional.of(URI.create("s3://hardcoded/default/")), bucketDb.archiveUriFor(ZoneId.defaultId(), TenantName.defaultName()));

        // Finds existing bucket in db when set to "auto"
        flagSource.withStringFlag(Flags.SYNC_HOST_LOGS_TO_S3_BUCKET.id(), "auto");
        assertEquals(Optional.of(URI.create("s3://existingBucket/default/")), bucketDb.archiveUriFor(ZoneId.defaultId(), TenantName.defaultName()));

        // Assigns to existing bucket while there is space
        IntStream.range(0, 29).forEach(i ->
                assertEquals(
                        Optional.of(URI.create("s3://existingBucket/tenant" + i + "/")), bucketDb
                                .archiveUriFor(ZoneId.defaultId(), TenantName.from("tenant" + i))));

        // Creates new bucket when existing buckets are full
        assertEquals(Optional.of(URI.create("s3://bucketArn/lastDrop/")), bucketDb.archiveUriFor(ZoneId.defaultId(), TenantName.from("lastDrop")));

        // Creates new bucket when there are no existing buckets in zone
        assertEquals(Optional.of(URI.create("s3://bucketArn/firstInZone/")), bucketDb.archiveUriFor(ZoneId.from("prod.us-east-3"), TenantName.from("firstInZone")));

        // Lists all buckets by zone
        Set<TenantName> existingBucketTenants = Streams.concat(Stream.of(TenantName.defaultName()), IntStream.range(0, 29).mapToObj(i -> TenantName.from("tenant" + i))).collect(Collectors.toUnmodifiableSet());
        assertEquals(
                Set.of(
                        new ArchiveBucket("existingBucket", "keyArn").withTenants(existingBucketTenants),
                        new ArchiveBucket("bucketArn", "keyArn").withTenant(TenantName.from("lastDrop"))),
                bucketDb.buckets(ZoneId.defaultId()));
        assertEquals(
                Set.of(new ArchiveBucket("bucketArn", "keyArn").withTenant(TenantName.from("firstInZone"))),
                bucketDb.buckets(ZoneId.from("prod.us-east-3")));
    }
}