// Copyright 2021 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.archive;

import com.yahoo.config.provision.zone.ZoneId;

import java.util.Map;

public interface ArchiveBucketDb {

    // TODO archive: add method(s) required by S3ArchiveService

    Map<ZoneId, String> zoneBuckets();
}
