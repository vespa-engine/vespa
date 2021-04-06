// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.yahoo.config.provision.TenantName;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.vespa.hosted.controller.api.integration.archive.ArchiveBucket;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * (de)serializes tenant/bucket mappings for a zone
 * <p>
 *
 * @author andreer
 */
public class ArchiveBucketsSerializer {

    // WARNING: Since there are multiple servers in a ZooKeeper cluster and they upgrade one by one
    //          (and rewrite all nodes on startup), changes to the serialized format must be made
    //          such that what is serialized on version N+1 can be read by version N:
    //          - ADDING FIELDS: Always ok
    //          - REMOVING FIELDS: Stop reading the field first. Stop writing it on a later version.
    //          - CHANGING THE FORMAT OF A FIELD: Don't do it bro.

    private final static String bucketsFieldName = "buckets";
    private final static String bucketArnFieldName = "bucketArn";
    private final static String keyArnFieldName = "keyArn";
    private final static String tenantsFieldName = "tenantIds";

    public static Slime toSlime(Set<ArchiveBucket> archiveBuckets) {
        Slime slime = new Slime();
        Cursor rootObject = slime.setObject();
        Cursor bucketsArray = rootObject.setArray(bucketsFieldName);

        archiveBuckets.forEach(bucket -> {
                    Cursor cursor = bucketsArray.addObject();
                    cursor.setString(bucketArnFieldName, bucket.bucketArn());
                    cursor.setString(keyArnFieldName, bucket.keyArn());
                    Cursor tenants = cursor.setArray(tenantsFieldName);
                    bucket.tenants().forEach(tenantName -> tenants.addString(tenantName.value()));
                }
        );

        return slime;
    }

    public static Set<ArchiveBucket> fromSlime(Inspector inspector) {
        Inspector buckets = inspector.field(bucketsFieldName);

        return IntStream.range(0, buckets.entries()).mapToObj(buckets::entry)
                .map(ArchiveBucketsSerializer::fromInspector).collect(Collectors.toUnmodifiableSet());
    }

    private static ArchiveBucket fromInspector(Inspector inspector) {
        Set<TenantName> tenants =
                IntStream.range(0, inspector.field(tenantsFieldName).entries())
                        .mapToObj(i -> inspector.field(tenantsFieldName).entry(i).asString())
                        .map(TenantName::from)
                        .collect(Collectors.toUnmodifiableSet());

        return new ArchiveBucket(
                inspector.field(bucketArnFieldName).asString(),
                inspector.field(keyArnFieldName).asString())
                .withTenants(tenants);
    }

    public static Set<ArchiveBucket> fromJsonString(String zkData) {
        return fromSlime(SlimeUtils.jsonToSlime(zkData).get());
    }
}
