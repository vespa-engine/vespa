// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.archive;

import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.tenant.ArchiveAccess;

import java.net.URI;
import java.util.Map;
import java.util.Set;

/**
 * Service that manages archive storage URIs for tenant nodes.
 *
 * @author freva
 * @author andreer
 */
public interface ArchiveService {

    ArchiveBucket createArchiveBucketFor(ZoneId zoneId);

    void updatePolicies(ZoneId zoneId, Set<ArchiveBucket> buckets, Map<TenantName,ArchiveAccess> authorizeAccessByTenantName);

    boolean canAddTenantToBucket(ZoneId zoneId, ArchiveBucket bucket);

    URI bucketURI(ZoneId zoneId, String bucketName, TenantName tenantName);
}
