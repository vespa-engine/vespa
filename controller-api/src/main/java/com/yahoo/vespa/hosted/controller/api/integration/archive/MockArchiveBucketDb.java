// Copyright 2021 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.archive;

import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.zone.ZoneId;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class MockArchiveBucketDb implements ArchiveBucketDb {

    private final Map<ZoneId, Map<TenantName, URI>> archiveUris = new HashMap<>();

    @Override
    public Optional<URI> archiveUriFor(ZoneId zoneId, TenantName tenant) {
        return Optional.ofNullable(archiveUris.get(zoneId)).map(uris -> uris.get(tenant));
    }

    @Override
    public Map<ZoneId, String> zoneBuckets() {
        return Map.of();
    }

    public void setArchiveUri(ZoneId zone, TenantName tenantName, URI archiveUri) {
        archiveUris.computeIfAbsent(zone, z -> new HashMap<>()).put(tenantName, archiveUri);
    }
}
