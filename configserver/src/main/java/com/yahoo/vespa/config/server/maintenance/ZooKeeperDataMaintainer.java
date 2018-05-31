// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
        package com.yahoo.vespa.config.server.maintenance;

import com.yahoo.path.Path;
import com.yahoo.vespa.config.server.ApplicationRepository;
import com.yahoo.vespa.curator.Curator;

import java.time.Duration;

/**
 * Removes unused zookeeper data (for now only data used by old file distribution code is removed)
 */
public class ZooKeeperDataMaintainer extends Maintainer {

    ZooKeeperDataMaintainer(ApplicationRepository applicationRepository, Curator curator, Duration interval) {
        super(applicationRepository, curator, interval);
    }

    @Override
    protected void maintain() {
        curator.delete(Path.fromString("/vespa/filedistribution"));
        curator.delete(Path.fromString("/vespa/config"));
    }
}
