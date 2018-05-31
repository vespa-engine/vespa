// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.maintenance;

import com.yahoo.path.Path;
import com.yahoo.vespa.curator.Curator;
import org.junit.Test;

import java.time.Duration;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ZooKeeperDataMaintainerTest {

    @Test
    public void deleteOldData() {
        MaintainerTester tester = new MaintainerTester();
        Curator curator = tester.curator();

        curator.create(Path.fromString("/foo"));
        curator.create(Path.fromString("/vespa/bar"));
        curator.create(Path.fromString("/vespa/filedistribution"));
        curator.create(Path.fromString("/vespa/config"));

        new ZooKeeperDataMaintainer(tester.applicationRepository(), curator, Duration.ofDays(1)).run();

        assertTrue(curator.exists(Path.fromString("/foo")));
        assertTrue(curator.exists(Path.fromString("/vespa")));
        assertFalse(curator.exists(Path.fromString("/vespa/filedistribution")));
        assertFalse(curator.exists(Path.fromString("/vespa/config")));
    }
}
