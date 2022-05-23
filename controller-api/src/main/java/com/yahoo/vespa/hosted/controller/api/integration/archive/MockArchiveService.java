// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.archive;

import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.zone.ZoneId;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * @author freva
 * @author andreer
 */
public class MockArchiveService implements ArchiveService {

    public Map<ArchiveBucket, Map<TenantName, String>> authorizedIamRolesForBucket = new HashMap<>();
    public Map<String, Set<String>> authorizedIamRolesForKey = new TreeMap<>();

    @Override
    public ArchiveBucket createArchiveBucketFor(ZoneId zoneId) {
        return new ArchiveBucket("bucketName", "keyArn");
    }

    @Override
    public void updateBucketPolicy(ZoneId zoneId, ArchiveBucket bucket, Map<TenantName, String> authorizeIamRoleByTenantName) {
        authorizedIamRolesForBucket.put(bucket, authorizeIamRoleByTenantName);
    }

    @Override
    public void updateKeyPolicy(ZoneId zoneId, String keyArn, Set<String> tenantAuthorizedIamRoles) {
        authorizedIamRolesForKey.put(keyArn, tenantAuthorizedIamRoles);
    }

    @Override
    public boolean canAddTenantToBucket(ZoneId zoneId, ArchiveBucket bucket) {
        return bucket.tenants().size() < 5;
    }

    @Override
    public URI bucketURI(ZoneId zoneId, String bucketName, TenantName tenantName) {
        return URI.create(String.format("s3://%s/%s/", bucketName, tenantName.value()));
    }
}
