// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.archive;

import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.zone.ZoneId;

import java.util.Map;
import java.util.Set;

/**
 * @author freva
 */
public class MockArchiveService implements ArchiveService {

    @Override
    public ArchiveBucket createArchiveBucketFor(ZoneId zoneId, Set<TenantName> tenantNames) {
        return new ArchiveBucket("bucketArn", "keyArn", tenantNames);
    }

    @Override
    public void updateBucketAndKeyPolicy(ZoneId zoneId, ArchiveBucket bucket, Map<TenantName, String> authorizeIamRoleByTenantName) {
        // noop
    }
}
