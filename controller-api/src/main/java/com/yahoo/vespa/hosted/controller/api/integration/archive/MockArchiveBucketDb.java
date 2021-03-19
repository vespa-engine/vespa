// Copyright 2021 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.archive;

import com.yahoo.config.provision.zone.ZoneId;

import java.util.Map;

public class MockArchiveBucketDb implements ArchiveBucketDb {
    @Override
    public Map<ZoneId, String> zoneBuckets() {
        return Map.of();
    }
}
