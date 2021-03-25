// Copyright 2021 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.archive;

import com.yahoo.component.AbstractComponent;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.zone.ZoneId;

import java.net.URI;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class MockArchiveBucketDb extends AbstractComponent implements ArchiveBucketDb {

    private final Map<ZoneId, Set<ArchiveBucket>> archiveBuckets = new HashMap<>();

    @Override
    public Optional<URI> archiveUriFor(ZoneId zoneId, TenantName tenant) {
        return archiveBuckets.getOrDefault(zoneId, Set.of()).stream()
                .filter(bucket -> bucket.tenants().contains(tenant))
                .findAny()
                .map(archiveBucket -> URI.create(archiveBucket.bucketArn()));
    }

    @Override
    public Set<ArchiveBucket> buckets(ZoneId zoneId) {
        return archiveBuckets.getOrDefault(zoneId, Set.of());
    }

    public void addBucket(ZoneId zoneId, ArchiveBucket archiveBucket) {
        archiveBuckets.computeIfAbsent(zoneId, z -> new LinkedHashSet<>()).add(archiveBucket);
    }
}
