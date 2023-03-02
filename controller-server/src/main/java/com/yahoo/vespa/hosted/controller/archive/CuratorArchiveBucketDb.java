// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.archive;

import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.archive.ArchiveBuckets;
import com.yahoo.vespa.hosted.controller.api.integration.archive.ArchiveService;
import com.yahoo.vespa.hosted.controller.api.integration.archive.TenantManagedArchiveBucket;
import com.yahoo.vespa.hosted.controller.api.integration.archive.VespaManagedArchiveBucket;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * This class decides which tenant goes in what bucket, and creates new buckets when required.
 *
 * @author andreer
 */
public class CuratorArchiveBucketDb {

    private static final Duration ENCLAVE_BUCKET_CACHE_LIFETIME = Duration.ofMinutes(60);

    /**
     * Archive URIs are often requested because they are returned in /application/v4 API. Since they
     * never change, it's safe to cache them and only update on misses
     */
    private final Map<ZoneId, Map<TenantName, String>> archiveUriCache = new ConcurrentHashMap<>();
    private final Map<ZoneId, Map<CloudAccount, TenantManagedArchiveBucket>> tenantArchiveCache = new ConcurrentHashMap<>();

    private final ArchiveService archiveService;
    private final CuratorDb curatorDb;
    private final Clock clock;

    public CuratorArchiveBucketDb(Controller controller) {
        this.archiveService = controller.serviceRegistry().archiveService();
        this.curatorDb = controller.curator();
        this.clock = controller.clock();
    }

    public Optional<URI> archiveUriFor(ZoneId zoneId, TenantName tenant, boolean createIfMissing) {
        return getBucketNameFromCache(zoneId, tenant)
                .or(() -> createIfMissing ? Optional.of(assignToBucket(zoneId, tenant)) : Optional.empty())
                .map(bucketName -> archiveService.bucketURI(zoneId, bucketName));
    }

    public Optional<URI> archiveUriFor(ZoneId zoneId, CloudAccount account, boolean searchIfMissing) {
        Instant updatedAfter = searchIfMissing ? clock.instant().minus(ENCLAVE_BUCKET_CACHE_LIFETIME) : Instant.MIN;
        return getBucketNameFromCache(zoneId, account, updatedAfter)
                .or(() -> {
                    if (!searchIfMissing) return Optional.empty();
                    try (var lock = curatorDb.lockArchiveBuckets(zoneId)) {
                        ArchiveBuckets archiveBuckets = buckets(zoneId);
                        updateArchiveUriCache(zoneId, archiveBuckets);

                        return getBucketNameFromCache(zoneId, account, updatedAfter)
                                .or(() -> archiveService.findEnclaveArchiveBucket(zoneId, account)
                                        .map(bucketName -> {
                                            var bucket = new TenantManagedArchiveBucket(bucketName, account, clock.instant());
                                            ArchiveBuckets updated = archiveBuckets.with(bucket);
                                            curatorDb.writeArchiveBuckets(zoneId, updated);
                                            updateArchiveUriCache(zoneId, updated);
                                            return bucket;
                                        }));
                    }
                })
                .map(TenantManagedArchiveBucket::bucketName)
                .map(bucketName -> archiveService.bucketURI(zoneId, bucketName));
    }

    private String assignToBucket(ZoneId zoneId, TenantName tenant) {
        try (var lock = curatorDb.lockArchiveBuckets(zoneId)) {
            ArchiveBuckets archiveBuckets = buckets(zoneId);
            updateArchiveUriCache(zoneId, archiveBuckets);

            return getBucketNameFromCache(zoneId, tenant) // Some other thread might have assigned it before we grabbed the lock
                    .orElseGet(() -> {
                        // If not, find an existing bucket with space
                        VespaManagedArchiveBucket bucketToAssignTo = archiveBuckets.vespaManaged().stream()
                                .filter(bucket -> archiveService.canAddTenantToBucket(zoneId, bucket))
                                .findAny()
                                // Or create a new one
                                .orElseGet(() -> archiveService.createArchiveBucketFor(zoneId));

                        ArchiveBuckets updated = archiveBuckets.with(bucketToAssignTo.withTenant(tenant));
                        curatorDb.writeArchiveBuckets(zoneId, updated);
                        updateArchiveUriCache(zoneId, updated);

                        return bucketToAssignTo.bucketName();
                    });
        }
    }

    public ArchiveBuckets buckets(ZoneId zoneId) {
        return curatorDb.readArchiveBuckets(zoneId);
    }

    private Optional<String> getBucketNameFromCache(ZoneId zoneId, TenantName tenantName) {
        return Optional.ofNullable(archiveUriCache.get(zoneId)).map(map -> map.get(tenantName));
    }

    private Optional<TenantManagedArchiveBucket> getBucketNameFromCache(ZoneId zoneId, CloudAccount cloudAccount, Instant updatedAfter) {
        return Optional.ofNullable(tenantArchiveCache.get(zoneId))
                .map(map -> map.get(cloudAccount))
                .filter(bucket -> bucket.updatedAt().isAfter(updatedAfter));
    }

    private void updateArchiveUriCache(ZoneId zoneId, ArchiveBuckets archiveBuckets) {
        Map<TenantName, String> bucketNameByTenant = archiveBuckets.vespaManaged().stream()
                .flatMap(bucket -> bucket.tenants().stream().map(tenant -> Map.entry(tenant, bucket.bucketName())))
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
        archiveUriCache.put(zoneId, bucketNameByTenant);

        Map<CloudAccount, TenantManagedArchiveBucket> bucketByAccount = archiveBuckets.tenantManaged().stream()
                .collect(Collectors.toUnmodifiableMap(TenantManagedArchiveBucket::cloudAccount, bucket -> bucket));
        tenantArchiveCache.put(zoneId, bucketByAccount);
    }
}
