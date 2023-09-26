// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.archive;

import com.yahoo.component.Version;
import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.tenant.ArchiveAccess;

import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

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

    /**
     * @return the version of the template that was used during the last apply for the given cloud account,
     *         or {@link Version#emptyVersion} if the version tag was not present or invalid,
     *         or {@link Optional#empty()} if the we have no access to the cloud account (template probably not applied yet)
     */
    Optional<Version> getEnclaveTemplateVersion(CloudAccount cloudAccount);

    static Stream<Version> parseVersion(String versionString) {
        try {
            return Stream.of(Version.fromString(versionString));
        } catch (IllegalArgumentException e) {
            return Stream.empty();
        }
    }
}
