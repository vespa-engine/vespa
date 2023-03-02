// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.hosted.controller.api.integration.archive.ArchiveBuckets;
import com.yahoo.vespa.hosted.controller.api.integration.archive.TenantManagedArchiveBucket;
import com.yahoo.vespa.hosted.controller.api.integration.archive.VespaManagedArchiveBucket;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ArchiveBucketsSerializerTest {

    @Test
    void serdes() {
        ArchiveBuckets archiveBuckets = new ArchiveBuckets(
                Set.of(new VespaManagedArchiveBucket("bucket1Name", "key1Arn").withTenants(Set.of(TenantName.from("t1"), TenantName.from("t2"))),
                       new VespaManagedArchiveBucket("bucket2Name", "key2Arn").withTenant(TenantName.from("t3"))),
                Set.of(new TenantManagedArchiveBucket("bucket3Name", CloudAccount.from("acct-1"), Instant.ofEpochMilli(1234)),
                       new TenantManagedArchiveBucket("bucket4Name", CloudAccount.from("acct-2"), Instant.ofEpochMilli(5678))));

        assertEquals(archiveBuckets, ArchiveBucketsSerializer.fromSlime(ArchiveBucketsSerializer.toSlime(archiveBuckets)));
    }
}
