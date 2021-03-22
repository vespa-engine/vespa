// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.archive;

import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.zone.ZoneId;

import java.net.URI;
import java.util.HashMap;
import java.util.Optional;
import java.util.Map;

/**
 * @author freva
 */
public class MockArchiveService implements ArchiveService {

    private final Map<ZoneId, Map<TenantName, URI>> archiveUris = new HashMap<>();

    @Override
    public String createArchiveBucketFor(ZoneId zoneId) {
        return "bucket-name";
    }

    @Override
    public void updateBucketAndKeyPolicy(ZoneId zoneId, String bucketName, Map<TenantName, String> authorizeIamRoleByTenantName) {
        // noop
    }
}
