// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.archive;

import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.tenant.ArchiveAccess;

import java.net.URI;
import java.time.Clock;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * @author freva
 * @author andreer
 */
public class MockArchiveService implements ArchiveService {

    private final Map<ZoneId, Set<TenantManagedArchiveBucket>> tenantArchiveBucketsByZone = new HashMap<>();
    public Set<VespaManagedArchiveBucket> archiveBuckets = new HashSet<>();
    public Map<TenantName, ArchiveAccess> authorizeAccessByTenantName = new HashMap<>();

    private final Clock clock;

    public MockArchiveService(Clock clock) {
        this.clock = clock;
    }

    @Override
    public VespaManagedArchiveBucket createArchiveBucketFor(ZoneId zoneId) {
        return new VespaManagedArchiveBucket("bucketName", "keyArn");
    }

    @Override
    public void updatePolicies(ZoneId zoneId, Set<VespaManagedArchiveBucket> buckets, Map<TenantName, ArchiveAccess> authorizeAccessByTenantName) {
        this.archiveBuckets = new HashSet<>(buckets);
        this.authorizeAccessByTenantName = new HashMap<>(authorizeAccessByTenantName);
    }

    @Override
    public boolean canAddTenantToBucket(ZoneId zoneId, VespaManagedArchiveBucket bucket) {
        return bucket.tenants().size() < 5;
    }

    @Override
    public Optional<String> findEnclaveArchiveBucket(ZoneId zoneId, CloudAccount cloudAccount) {
        return tenantArchiveBucketsByZone.getOrDefault(zoneId, Set.of()).stream()
                .filter(bucket -> bucket.cloudAccount().equals(cloudAccount))
                .findFirst()
                .map(TenantManagedArchiveBucket::bucketName);
    }

    @Override
    public URI bucketURI(ZoneId zoneId, String bucketName) {
        return URI.create(String.format("s3://%s/", bucketName));
    }


    public void setEnclaveArchiveBucket(ZoneId zoneId, CloudAccount cloudAccount, String bucketName) {
        removeEnclaveArchiveBucket(zoneId, cloudAccount);
        tenantArchiveBucketsByZone.computeIfAbsent(zoneId, z -> new HashSet<>())
                .add(new TenantManagedArchiveBucket(bucketName, cloudAccount, clock.instant()));
    }

    public void removeEnclaveArchiveBucket(ZoneId zoneId, CloudAccount cloudAccount) {
        Optional.ofNullable(tenantArchiveBucketsByZone.get(zoneId))
                .ifPresent(set -> set.removeIf(bucket -> bucket.cloudAccount().equals(cloudAccount)));
    }
}
