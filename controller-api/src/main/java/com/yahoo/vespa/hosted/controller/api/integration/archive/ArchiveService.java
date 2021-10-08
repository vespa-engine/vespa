// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.archive;

import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.zone.ZoneId;

import java.util.Map;

/**
 * Service that manages archive storage URIs for tenant nodes.
 *
 * @author freva
 * @author andreer
 */
public interface ArchiveService {

    ArchiveBucket createArchiveBucketFor(ZoneId zoneId, boolean sharded);

    void updateBucketAndKeyPolicy(ZoneId zoneId, ArchiveBucket bucket, Map<TenantName, String> authorizeIamRoleByTenantName);
}
