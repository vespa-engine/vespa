// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.archive;

import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.tenant.ArchiveAccess;

import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Service that manages archive storage URIs for tenant nodes.
 *
 * @author freva
 * @author andreer
 */
public interface ArchiveService {

    VespaManagedArchiveBucket createArchiveBucketFor(ZoneId zoneId);

    void updatePolicies(ZoneId zoneId, Set<VespaManagedArchiveBucket> buckets, Map<TenantName,ArchiveAccess> authorizeAccessByTenantName);

    boolean canAddTenantToBucket(ZoneId zoneId, VespaManagedArchiveBucket bucket);

    Optional<String> findEnclaveArchiveBucket(ZoneId zoneId, CloudAccount cloudAccount);

    URI bucketURI(ZoneId zoneId, String bucketName);
}
