// Copyright 2021 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.archive;

import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.text.Text;
import com.yahoo.vespa.flags.BooleanFlag;
import com.yahoo.vespa.flags.FetchVector;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.archive.ArchiveBucket;
import com.yahoo.vespa.hosted.controller.api.integration.archive.ArchiveService;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;

import java.net.URI;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * This class decides which tenant goes in what bucket, and creates new buckets when required.
 *
 * @author andreer
 */
public class CuratorArchiveBucketDb {

    /**
     * Archive URIs are often requested because they are returned in /application/v4 API. Since they
     * never change, it's safe to cache them and only update on misses
     */
    private final Map<ZoneId, Map<TenantName, String>> archiveUriCache = new ConcurrentHashMap<>();

    private final ArchiveService archiveService;
    private final CuratorDb curatorDb;
    private final BooleanFlag enableFlag;
    private final SystemName system;

    public CuratorArchiveBucketDb(Controller controller) {
        this.archiveService = controller.serviceRegistry().archiveService();
        this.curatorDb = controller.curator();
        this.enableFlag = Flags.ENABLE_ONPREM_TENANT_S3_ARCHIVE.bindTo(controller.flagSource());
        this.system = controller.zoneRegistry().system();
    }

    public Optional<URI> archiveUriFor(ZoneId zoneId, TenantName tenant) {
        if (enabled(zoneId, tenant)) {
            return Optional.of(URI.create(Text.format("s3://%s/%s/", findOrAssignBucket(zoneId, tenant), tenant.value())));
        } else {
            return Optional.empty();
        }
    }

    private boolean enabled(ZoneId zone, TenantName tenant) {
        return system.isPublic() ||
                enableFlag
                        .with(FetchVector.Dimension.ZONE_ID, zone.value())
                        .with(FetchVector.Dimension.TENANT_ID, tenant.value())
                        .value();
    }

    private String findOrAssignBucket(ZoneId zoneId, TenantName tenant) {
        return getBucketNameFromCache(zoneId, tenant)
                .or(() -> findAndUpdateArchiveUriCache(zoneId, tenant, buckets(zoneId)))
                .orElseGet(() -> assignToBucket(zoneId, tenant));
    }

    private String assignToBucket(ZoneId zoneId, TenantName tenant) {
        try (var lock = curatorDb.lockArchiveBuckets(zoneId)) {
            Set<ArchiveBucket> zoneBuckets = new HashSet<>(buckets(zoneId));

            return findAndUpdateArchiveUriCache(zoneId, tenant, zoneBuckets) // Some other thread might have assigned it before we grabbed the lock
                    .orElseGet(() -> {
                        // If not, find an existing bucket with space
                        Optional<ArchiveBucket> unfilledBucket = zoneBuckets.stream()
                                .filter(bucket -> bucket.tenants().size() < tenantsPerBucket().orElse(Integer.MAX_VALUE))
                                .findAny();

                        // And place the tenant in that bucket.
                        if (unfilledBucket.isPresent()) {
                            var unfilled = unfilledBucket.get();

                            zoneBuckets.remove(unfilled);
                            zoneBuckets.add(unfilled.withTenant(tenant));
                            curatorDb.writeArchiveBuckets(zoneId, zoneBuckets);

                            return unfilled.bucketName();
                        }

                        // We'll have to create a new bucket
                        var newBucket = archiveService.createArchiveBucketFor(zoneId, tenantsPerBucket().isPresent())
                                .withTenant(tenant);
                        zoneBuckets.add(newBucket);
                        curatorDb.writeArchiveBuckets(zoneId, zoneBuckets);
                        updateArchiveUriCache(zoneId, zoneBuckets);
                        return newBucket.bucketName();
                    });
        }
    }

    public Set<ArchiveBucket> buckets(ZoneId zoneId) {
        return curatorDb.readArchiveBuckets(zoneId);
    }

    private Optional<String> findAndUpdateArchiveUriCache(ZoneId zoneId, TenantName tenant, Set<ArchiveBucket> zoneBuckets) {
        Optional<String> bucketName = zoneBuckets.stream()
                .filter(bucket -> bucket.tenants().contains(tenant))
                .findAny()
                .map(ArchiveBucket::bucketName);
        if (bucketName.isPresent()) updateArchiveUriCache(zoneId, zoneBuckets);
        return bucketName;
    }

    private OptionalInt tenantsPerBucket() {
        if (system.isPublic()) {
            /*
             * Due to policy limits, we can't put data for more than this many tenants in a bucket.
             * Policy size limit is 20kb, about 550 bytes for non-tenant related policies. Each tenant
             * needs about 500 + len(role_arn) bytes, we limit role_arn to 100 characters, so we can
             * fit about (20k - 550) / 600 ~ 32 tenants per bucket.
             */
            return OptionalInt.of(30);
        } else {
            /*
             * The S3 policies in main/cd have a fixed size.
             */
            return OptionalInt.empty();
        }
    }

    private Optional<String> getBucketNameFromCache(ZoneId zoneId, TenantName tenantName) {
        return Optional.ofNullable(archiveUriCache.get(zoneId)).map(map -> map.get(tenantName));
    }

    private void updateArchiveUriCache(ZoneId zoneId, Set<ArchiveBucket> zoneBuckets) {
        Map<TenantName, String> bucketNameByTenant = zoneBuckets.stream()
                .flatMap(bucket -> bucket.tenants().stream()
                        .map(tenant -> Map.entry(tenant, bucket.bucketName())))
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
        archiveUriCache.put(zoneId, bucketNameByTenant);
    }
}
