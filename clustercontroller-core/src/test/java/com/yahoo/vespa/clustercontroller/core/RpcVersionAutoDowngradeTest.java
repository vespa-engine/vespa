// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.vdslib.distribution.ConfiguredNode;
import com.yahoo.vdslib.state.NodeState;
import com.yahoo.vdslib.state.State;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class RpcVersionAutoDowngradeTest extends FleetControllerTest {

    private void setUpFakeCluster(int nodeRpcVersion) throws Exception {
        List<ConfiguredNode> configuredNodes = new ArrayList<>();
        for (int i = 0 ; i < 10; i++) {
            configuredNodes.add(new ConfiguredNode(i, false));
        }
        FleetControllerOptions options = new FleetControllerOptions("mycluster", configuredNodes);
        setUpFleetController(false, options);
        DummyVdsNodeOptions nodeOptions = new DummyVdsNodeOptions();
        nodeOptions.stateCommunicationVersion = nodeRpcVersion;
        List<DummyVdsNode> nodes = setUpVdsNodes(false, nodeOptions, true, configuredNodes);
        for (DummyVdsNode node : nodes) {
            node.setNodeState(new NodeState(node.getType(), State.UP).setMinUsedBits(20));
            node.connect();
        }
    }

    @Test
    public void cluster_state_rpc_version_is_auto_downgraded_and_retried_for_older_nodes() throws Exception {
        setUpFakeCluster(2); // HEAD is at v3
        waitForState("version:\\d+ distributor:10 storage:10");
    }

}
