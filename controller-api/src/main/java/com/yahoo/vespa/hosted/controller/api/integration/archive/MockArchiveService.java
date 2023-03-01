// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.archive;

import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.tenant.ArchiveAccess;

import java.net.URI;
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


    public Set<ArchiveBucket> archiveBuckets = new HashSet<>();
    public Map<TenantName, ArchiveAccess> authorizeAccessByTenantName = new HashMap<>();


    @Override
    public ArchiveBucket createArchiveBucketFor(ZoneId zoneId) {
        return new ArchiveBucket("bucketName", "keyArn");
    }

    @Override
    public void updatePolicies(ZoneId zoneId, Set<ArchiveBucket> buckets, Map<TenantName, ArchiveAccess> authorizeAccessByTenantName) {
        this.archiveBuckets = new HashSet<>(buckets);
        this.authorizeAccessByTenantName = new HashMap<>(authorizeAccessByTenantName);
    }

    @Override
    public boolean canAddTenantToBucket(ZoneId zoneId, ArchiveBucket bucket) {
        return bucket.tenants().size() < 5;
    }

    @Override
    public Optional<String> findEnclaveArchiveBucket(ZoneId zoneId, CloudAccount cloudAccount) {
        return Optional.empty();
    }

    @Override
    public URI bucketURI(ZoneId zoneId, String bucketName) {
        return URI.create(String.format("s3://%s/", bucketName));
    }
}
