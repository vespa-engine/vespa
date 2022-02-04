// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.archive;

import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.zone.ZoneId;

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
}
