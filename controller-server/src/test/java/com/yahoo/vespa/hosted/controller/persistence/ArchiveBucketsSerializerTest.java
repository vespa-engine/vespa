// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.hosted.controller.api.integration.archive.ArchiveBucket;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ArchiveBucketsSerializerTest {

    @Test
    void serdes() {
        var testTenants = new LinkedHashSet<TenantName>();
        testTenants.add(TenantName.from("tenant1"));
        testTenants.add(TenantName.from("tenant2"));

        var testBuckets = new LinkedHashSet<ArchiveBucket>();
        testBuckets.add(new ArchiveBucket("bucket1Name", "key1Arn").withTenants(testTenants));
        testBuckets.add(new ArchiveBucket("bucket2Name", "key2Arn"));

        String zkData = "{\"buckets\":[{\"bucketName\":\"bucket1Name\",\"keyArn\":\"key1Arn\",\"tenantIds\":[\"tenant1\",\"tenant2\"]},{\"bucketName\":\"bucket2Name\",\"keyArn\":\"key2Arn\",\"tenantIds\":[]}]}";

        assertEquals(testBuckets, ArchiveBucketsSerializer.fromJsonString(zkData));
        assertEquals(testBuckets, ArchiveBucketsSerializer.fromJsonString(ArchiveBucketsSerializer.toSlime(testBuckets).toString()));
    }
}
