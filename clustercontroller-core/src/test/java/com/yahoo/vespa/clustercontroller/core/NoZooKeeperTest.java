// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class NoZooKeeperTest extends FleetControllerTest {

    @Test
    void testWantedStatesInZooKeeper() throws Exception {
        // Null is the default for zooKeeperServerAddress
        FleetControllerOptions.Builder builder = defaultOptions();
        Timer timer = new FakeTimer();
        setUpFleetController(timer, builder);
        setUpVdsNodes(timer);
        waitForStableSystem();

        assertTrue(nodes.get(0).isDistributor());
        nodes.get(0).disconnect();
        waitForState("version:\\d+ distributor:10 .0.s:d storage:10");

        nodes.get(0).connect();
        waitForState("version:\\d+ distributor:10 storage:10");
    }
}
