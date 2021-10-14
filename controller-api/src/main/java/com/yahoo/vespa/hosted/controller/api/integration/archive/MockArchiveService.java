// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.archive;

import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.zone.ZoneId;

import java.util.HashMap;
import java.util.Map;

/**
 * @author freva
 * @author andreer
 */
public class MockArchiveService implements ArchiveService {

    public Map<ArchiveBucket, Map<TenantName, String>> authorizedIamRoles = new HashMap<>();

    @Override
    public ArchiveBucket createArchiveBucketFor(ZoneId zoneId, boolean sharded) {
        return new ArchiveBucket("bucketName", "keyArn");
    }

    @Override
    public void updateBucketAndKeyPolicy(ZoneId zoneId, ArchiveBucket bucket, Map<TenantName, String> authorizeIamRoleByTenantName) {
        authorizedIamRoles.put(bucket, authorizeIamRoleByTenantName);
    }
}
