// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.archive;

import java.util.HashSet;
import java.util.Set;

/**
 * @author freva
 */
public record ArchiveBuckets(Set<VespaManagedArchiveBucket> vespaManaged,
                             Set<TenantManagedArchiveBucket> tenantManaged) {
    public static final ArchiveBuckets EMPTY = new ArchiveBuckets(Set.of(), Set.of());

    public ArchiveBuckets(Set<VespaManagedArchiveBucket> vespaManaged, Set<TenantManagedArchiveBucket> tenantManaged) {
        this.vespaManaged = Set.copyOf(vespaManaged);
        this.tenantManaged = Set.copyOf(tenantManaged);
    }

    /** Adds or replaces a VespaManagedArchive bucket with the given archive bucket */
    public ArchiveBuckets with(VespaManagedArchiveBucket vespaManagedArchiveBucket) {
        Set<VespaManagedArchiveBucket> updated = new HashSet<>(vespaManaged);
        updated.removeIf(bucket -> bucket.bucketName().equals(vespaManagedArchiveBucket.bucketName()));
        updated.add(vespaManagedArchiveBucket);
        return new ArchiveBuckets(updated, tenantManaged);
    }

    /** Adds or replaces a TenantManagedArchive bucket with the given archive bucket */
    public ArchiveBuckets with(TenantManagedArchiveBucket tenantManagedArchiveBucket) {
        Set<TenantManagedArchiveBucket> updated = new HashSet<>(tenantManaged);
        updated.removeIf(bucket -> bucket.cloudAccount().equals(tenantManagedArchiveBucket.cloudAccount()));
        updated.add(tenantManagedArchiveBucket);
        return new ArchiveBuckets(vespaManaged, updated);
    }
}
