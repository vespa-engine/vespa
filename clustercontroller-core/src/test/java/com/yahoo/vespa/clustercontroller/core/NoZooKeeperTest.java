// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class NoZooKeeperTest extends FleetControllerTest {

    @Test
    public void testWantedStatesInZooKeeper() throws Exception {
        startingTest("NoZooKeeperTest::testWantedStatesInZooKeeper");
        FleetControllerOptions options = new FleetControllerOptions("mycluster");
        options.zooKeeperServerAddress = null;
        setUpFleetController(true, options);
        setUpVdsNodes(true, new DummyVdsNodeOptions());
        waitForStableSystem();

        assertEquals(true, nodes.get(0).isDistributor());
        nodes.get(0).disconnect();
        waitForState("version:\\d+ distributor:10 .0.s:d storage:10");

        nodes.get(0).connect();
        waitForState("version:\\d+ distributor:10 storage:10");
    }
}
