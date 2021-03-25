package com.yahoo.vespa.hosted.controller.persistence;

import com.yahoo.vespa.hosted.controller.api.identifiers.TenantId;
import com.yahoo.vespa.hosted.controller.api.integration.archive.ArchiveBucket;
import org.junit.Test;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class ArchiveBucketsSerializerTest {

    @Test
    public void serdes() {
        var testTenants = new LinkedHashSet<TenantId>();
        testTenants.add(new TenantId("tenant1"));
        testTenants.add(new TenantId("tenant2"));

        var testBuckets = new LinkedHashSet<ArchiveBucket>();
        testBuckets.add(new ArchiveBucket("bucket1Arn", Optional.of("key1Arn"), testTenants));
        testBuckets.add(new ArchiveBucket("bucket2Arn", Optional.empty(), Set.of()));

        String zkData = "{\"buckets\":[{\"bucketArn\":\"bucket1Arn\",\"keyArn\":\"key1Arn\",\"tenantIds\":[\"tenant1\",\"tenant2\"]},{\"bucketArn\":\"bucket2Arn\",\"tenantIds\":[]}]}";

        assertEquals(ArchiveBucketsSerializer.toSlime(testBuckets).toString(), zkData);
        assertEquals(ArchiveBucketsSerializer.fromJsonString(zkData), testBuckets);
    }
}
