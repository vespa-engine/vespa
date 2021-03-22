// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.archive;

import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.zone.ZoneId;

import java.net.URI;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * Service that manages archive storage URIs for tenant nodes.
 *
 * @author freva
 */
public interface ArchiveService {

    public String createArchiveBucketFor(ZoneId zoneId);

    void updateBucketAndKeyPolicy(ZoneId zoneId, String bucketName, Map<TenantName, String> authorizeIamRoleByTenantName);
}
