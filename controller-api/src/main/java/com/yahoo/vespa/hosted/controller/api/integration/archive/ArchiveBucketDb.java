// Copyright 2021 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.archive;

import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.zone.ZoneId;

import java.net.URI;
import java.util.Optional;
import java.util.Set;

public interface ArchiveBucketDb {

    Optional<URI> archiveUriFor(ZoneId zoneId, TenantName tenant);

    Set<ArchiveBucket> buckets(ZoneId zoneId);
}
