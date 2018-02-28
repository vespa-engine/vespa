// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.vdslib.distribution.ConfiguredNode;
import com.yahoo.vdslib.state.ClusterState;
import com.yahoo.vdslib.state.NodeState;
import com.yahoo.vdslib.state.NodeType;
import com.yahoo.vdslib.state.State;
import org.junit.Ignore;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

public class DistributionBitCountTest extends FleetControllerTest {

    private void setUpSystem(String testName) throws Exception {
        List<ConfiguredNode> configuredNodes = new ArrayList<>();
        for (int i = 0 ; i < 10; i++) {
            configuredNodes.add(new ConfiguredNode(i, false));
        }
        FleetControllerOptions options = new FleetControllerOptions("mycluster", configuredNodes);
        options.distributionBits = 17;
        setUpFleetController(false, options);
        startingTest(testName);
        List<DummyVdsNode> nodes = setUpVdsNodes(false, new DummyVdsNodeOptions(), true, configuredNodes);
        for (DummyVdsNode node : nodes) {
            node.setNodeState(new NodeState(node.getType(), State.UP).setMinUsedBits(20));
            node.connect();
        }
        waitForState("version:\\d+ bits:17 distributor:10 storage:10");
    }

    /**
     * Test that then altering config to increased bit count, that a new system state is sent out if the least split storagenode use more bits.
     * Test that then altering config to increased bit count, that a new system state is not sent out (and not altered) if a storagenode needs it to be no further split.
     */
    @Test
    public void testDistributionBitCountConfigIncrease() throws Exception {
        setUpSystem("DistributionBitCountTest::testDistributionBitCountConfigIncrease");
        options.distributionBits = 20;
        fleetController.updateOptions(options, 0);
        ClusterState currentState = waitForState("version:\\d+ bits:20 distributor:10 storage:10");

        int version = currentState.getVersion();
        options.distributionBits = 23;
        fleetController.updateOptions(options, 0);
        assertEquals(version, currentState.getVersion());
    }

    /**
     * Test that then altering config to decrease bit count, that a new system state is sent out with that bit count.
     */
    @Test
    public void testDistributionBitCountConfigDecrease() throws Exception {
        setUpSystem("DistributionBitCountTest::testDistributionBitCountConfigDecrease");
        options.distributionBits = 12;
        fleetController.updateOptions(options, 0);
        waitForState("version:\\d+ bits:12 distributor:10 storage:10");
    }


    /**
     * Test that when storage node reports higher bit count, but another storage
     * node has equally low bitcount, the fleetcontroller does nothing.
     *
     * Test that when storage node reports higher bit count, but another storage
     * node now being lowest, the fleetcontroller adjusts to use that bit in system state.
     */
    @Test
    public void testStorageNodeReportingHigherBitCount() throws Exception {
        setUpSystem("DistributionBitCountTest::testStorageNodeReportingHigherBitCount");

        nodes.get(1).setNodeState(new NodeState(NodeType.STORAGE, State.UP).setMinUsedBits(11));
        nodes.get(3).setNodeState(new NodeState(NodeType.STORAGE, State.UP).setMinUsedBits(11));

        ClusterState startState = waitForState("version:\\d+ bits:11 distributor:10 storage:10");

        nodes.get(1).setNodeState(new NodeState(NodeType.STORAGE, State.UP).setMinUsedBits(12));
        assertEquals(startState + "->" + fleetController.getSystemState(),
                     startState.getVersion(), fleetController.getSystemState().getVersion());

        for (int i = 0; i < 10; ++i) {
            // nodes is array of [distr.0, stor.0, distr.1, stor.1, ...] and we just want the storage nodes
            nodes.get(i*2 + 1).setNodeState(new NodeState(NodeType.STORAGE, State.UP).setMinUsedBits(17));
        }
        assertEquals(startState.getVersion() + 1, waitForState("version:\\d+ bits:17 distributor:10 storage:10").getVersion());
    }

    /**
     * Test that then storage node report lower bit count, but another storage node with equally low bitcount, the fleetcontroller does nothing.
     * Test that then storage node report lower bit count, and then becomes the smallest, the fleetcontroller adjusts to use that bit in system state.
     */
    @Test
    public void testStorageNodeReportingLowerBitCount() throws Exception {
        setUpSystem("DistributionBitCountTest::testStorageNodeReportingLowerBitCount");

        nodes.get(1).setNodeState(new NodeState(NodeType.STORAGE, State.UP).setMinUsedBits(13));
        ClusterState currentState = waitForState("version:\\d+ bits:13 distributor:10 storage:10");
        int version = currentState.getVersion();

        nodes.get(3).setNodeState(new NodeState(NodeType.STORAGE, State.UP).setMinUsedBits(15));
        assertEquals(version, currentState.getVersion());

        nodes.get(3).setNodeState(new NodeState(NodeType.STORAGE, State.UP).setMinUsedBits(13));
        assertEquals(version, currentState.getVersion());

        nodes.get(3).setNodeState(new NodeState(NodeType.STORAGE, State.UP).setMinUsedBits(12));
        waitForState("version:\\d+ bits:12 distributor:10 storage:10");
    }

}
