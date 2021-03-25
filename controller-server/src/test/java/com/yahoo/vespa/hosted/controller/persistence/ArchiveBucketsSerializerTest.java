package com.yahoo.vespa.hosted.controller.persistence;

import com.yahoo.vespa.hosted.controller.api.identifiers.TenantId;
import com.yahoo.vespa.hosted.controller.api.integration.archive.ArchiveBucket;
import org.junit.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class ArchiveBucketsSerializerTest {

    @Test
    public void serdes() {
        ArchiveBucket archiveBucket1 = new ArchiveBucket("bucket1Arn", Optional.of("key1Arn"), Set.of(new TenantId("vespa"), new TenantId("kraune")));
        ArchiveBucket archiveBucket2 = new ArchiveBucket("bucket2Arn", Optional.empty(), Set.of());
        Set<ArchiveBucket> testBuckets = Set.of(archiveBucket1, archiveBucket2);

        String zkData = "{\"buckets\":[{\"bucketArn\":\"bucket1Arn\",\"keyArn\":\"key1Arn\",\"tenantIds\":[\"vespa\",\"kraune\"]},{\"bucketArn\":\"bucket2Arn\",\"tenantIds\":[]}]}";

        assertEquals(ArchiveBucketsSerializer.toSlime(testBuckets).toString(), zkData);
        assertEquals(ArchiveBucketsSerializer.fromJsonString(zkData), testBuckets);
    }
}
