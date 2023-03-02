// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.TenantName;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.vespa.hosted.controller.api.integration.archive.ArchiveBuckets;
import com.yahoo.vespa.hosted.controller.api.integration.archive.TenantManagedArchiveBucket;
import com.yahoo.vespa.hosted.controller.api.integration.archive.VespaManagedArchiveBucket;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * (de)serializes tenant/bucket mappings for a zone
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

    private final static String vespaManagedBucketsFieldName = "buckets";
    private final static String tenantManagedBucketsFieldName = "tenantManagedBuckets";
    private final static String bucketNameFieldName = "bucketName";
    private final static String keyArnFieldName = "keyArn";
    private final static String tenantsFieldName = "tenantIds";
    private final static String accountFieldName = "account";
    private final static String updatedAtFieldName = "updatedAt";

    public static Slime toSlime(ArchiveBuckets archiveBuckets) {
        Slime slime = new Slime();
        Cursor rootObject = slime.setObject();

        Cursor vespaBucketsArray = rootObject.setArray(vespaManagedBucketsFieldName);
        archiveBuckets.vespaManaged().forEach(bucket -> {
            Cursor cursor = vespaBucketsArray.addObject();
            cursor.setString(bucketNameFieldName, bucket.bucketName());
            cursor.setString(keyArnFieldName, bucket.keyArn());
            Cursor tenants = cursor.setArray(tenantsFieldName);
            bucket.tenants().forEach(tenantName -> tenants.addString(tenantName.value()));
        });

        Cursor tenantBucketsArray = rootObject.setArray(tenantManagedBucketsFieldName);
        archiveBuckets.tenantManaged().forEach(bucket -> {
            Cursor cursor = tenantBucketsArray.addObject();
            cursor.setString(bucketNameFieldName, bucket.bucketName());
            cursor.setString(accountFieldName, bucket.cloudAccount().value());
            cursor.setLong(updatedAtFieldName, bucket.updatedAt().toEpochMilli());
        });

        return slime;
    }

    public static ArchiveBuckets fromSlime(Slime slime) {
        Inspector inspector = slime.get();
        return new ArchiveBuckets(
                SlimeUtils.entriesStream(inspector.field(vespaManagedBucketsFieldName))
                        .map(ArchiveBucketsSerializer::vespaManagedArchiveBucketFromInspector)
                        .collect(Collectors.toUnmodifiableSet()),
                SlimeUtils.entriesStream(inspector.field(tenantManagedBucketsFieldName))
                        .map(ArchiveBucketsSerializer::tenantManagedArchiveBucketFromInspector)
                        .collect(Collectors.toUnmodifiableSet()));
    }

    private static VespaManagedArchiveBucket vespaManagedArchiveBucketFromInspector(Inspector inspector) {
        Set<TenantName> tenants = SlimeUtils.entriesStream(inspector.field(tenantsFieldName))
                .map(i -> TenantName.from(i.asString()))
                .collect(Collectors.toUnmodifiableSet());

        return new VespaManagedArchiveBucket(
                inspector.field(bucketNameFieldName).asString(),
                inspector.field(keyArnFieldName).asString())
                .withTenants(tenants);
    }

    private static TenantManagedArchiveBucket tenantManagedArchiveBucketFromInspector(Inspector inspector) {
        return new TenantManagedArchiveBucket(
                inspector.field(bucketNameFieldName).asString(),
                CloudAccount.from(inspector.field(accountFieldName).asString()),
                SlimeUtils.instant(inspector.field(updatedAtFieldName)));
    }
}
