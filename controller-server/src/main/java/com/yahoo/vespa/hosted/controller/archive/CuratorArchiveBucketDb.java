// Copyright 2021 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.archive;

import com.yahoo.component.AbstractComponent;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.api.integration.archive.ArchiveBucketDb;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;

import java.util.Map;

public class CuratorArchiveBucketDb extends AbstractComponent implements ArchiveBucketDb {

    public CuratorArchiveBucketDb(CuratorDb curatorDb) {
    }

    // TODO archive: store things in curator

    @Override
    public Map<ZoneId, String> zoneBuckets() {
        return null;
    }
}
